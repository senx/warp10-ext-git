//
//   Copyright 2020  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.ext.git;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.revwalk.RevCommit;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.ext.capabilities.Capabilities;;

public class GITSTORE extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public GITSTORE(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object top = stack.pop();

    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + " expects a parameter MAP.");
    }

    Map<Object,Object> params = (Map<Object,Object>) top;

    if (!(params.get(GitWarpScriptExtension.PARAM_MESSAGE) instanceof String)) {
      throw new WarpScriptException(getName() + " expects a commit message under key '" + GitWarpScriptExtension.PARAM_MESSAGE + "'.");
    }

    String message = (String) params.get(GitWarpScriptExtension.PARAM_MESSAGE);

    if (!(params.get(GitWarpScriptExtension.PARAM_PATH) instanceof String)) {
      throw new WarpScriptException(getName() + " unset path under key '" + GitWarpScriptExtension.PARAM_PATH + "'.");
    }

    String path = (String) params.get(GitWarpScriptExtension.PARAM_PATH);

    if (!(params.get(GitWarpScriptExtension.PARAM_REPO) instanceof String)) {
      throw new WarpScriptException(getName() + " unset repository under key '" + GitWarpScriptExtension.PARAM_REPO + "'.");
    }

    boolean noworkdir = Boolean.FALSE.equals(params.get(GitWarpScriptExtension.PARAM_WORKDIR));

    String repo = (String) params.get(GitWarpScriptExtension.PARAM_REPO);

    byte[] content = null;

    if (params.get(GitWarpScriptExtension.PARAM_CONTENT) instanceof String) {
      content = ((String) params.get(GitWarpScriptExtension.PARAM_CONTENT)).getBytes(StandardCharsets.UTF_8);
    } else if (params.get(GitWarpScriptExtension.PARAM_CONTENT) instanceof byte[]) {
      content = (byte[]) params.get(GitWarpScriptExtension.PARAM_CONTENT);
    } else {
      throw new WarpScriptException(getName() + " can only store content of type STRING or BYTES, specified under key '" + GitWarpScriptExtension.PARAM_CONTENT + "'.");
    }

    //
    // Check that the root is configured and that the stack has the correct capability
    //

    if (null == GitWarpScriptExtension.getRoot()) {
      throw new WarpScriptException(getName() + " Git root was not configured.");
    }

    //
    // Check that the stack has the right capability
    //
    Map<String,String> capabilities = Capabilities.get(stack, (List) null);

    if (!repo.equals(capabilities.get(GitWarpScriptExtension.CAP_GITREPO))) {
      throw new WarpScriptException(getName() + " missing or invalid '" + GitWarpScriptExtension.CAP_GITREPO + "' capability.");
    }

    if (null != capabilities.get(GitWarpScriptExtension.CAP_GITRO)) {
      throw new WarpScriptException(getName() + " no right to modify repository '" + repo + "'.");
    }

    //
    // Add git.subdir prefix to path if defined
    //

    if (null != capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR)) {
      path = capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR) + "/" + path;
    }

    //
    // Create the directories needed for 'path' and store 'content' in path
    //

    if (path.contains("/../") || path.contains("/./") || path.startsWith("./") || path.startsWith("../") || path.startsWith("/")) {
      throw new WarpScriptException(getName() + " invalid path.");
    }

    File repodir = new File(GitWarpScriptExtension.getRoot(), repo);
    File target = new File(repodir, path);

    Git git = null;

    try {
      git = Git.open(new File(GitWarpScriptExtension.getRoot(), repo));

      if (!target.exists()) {
        try {
          FileUtils.forceMkdir(target.getParentFile());
        } catch (IOException ioe) {
          throw new WarpScriptException(getName() + " error creating path.", ioe);
        }
      } else if (target.exists() && target.isDirectory()) {
        throw new WarpScriptException(getName() + " path points to a directory.");
      }

      try {
        FileUtils.writeByteArrayToFile(target, content);
      } catch (IOException ioe) {
        throw new WarpScriptException(getName() + " error writing file content.", ioe);
      }

      AddCommand add = git.add();
      add.addFilepattern(path);
      add.call();

      CommitCommand commit = git.commit();
      // Extract author and email from capabilities gituser and gitemail if set
      commit.setAuthor(capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITNAME, "warp10-ext-git"), capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITEMAIL, "contact@senx.io"));
      commit.setAllowEmpty(false);
      commit.setCommitter("warp10-ext-git", "contact@senx.io");
      commit.setMessage(message);

      RevCommit rev = commit.call();

      // Attempt to remove the file, we do not need it since it is now under git's responsibility
      // This might lead to incorrect content being written if another thread is attempting to
      // write the same file at the same instant, but such a situation could also occur without the
      // delete operation anyways, so might as well clean up after our own commit.
      if (noworkdir) {
        org.eclipse.jgit.util.FileUtils.delete(target, org.eclipse.jgit.util.FileUtils.IGNORE_ERRORS);
      }
      stack.push(rev.getId().name());
    } catch (EmptyCommitException ece) {
      stack.push(null);
    } catch (Exception e) {
      // Do not include original exception so we do not leak internal path
      throw new WarpScriptException(getName() + " error opening Git repository '" + repo + "'.");
    } finally {
      if (null != git) {
        git.close();
      }
    }

    return stack;
  }
}
