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
import org.eclipse.jgit.revwalk.RevCommit;

import io.warp10.ext.capabilities.Capabilities;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

public class GITCOMMIT extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public GITCOMMIT(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object top = stack.pop();

    if (!(top instanceof String)) {
      throw new WarpScriptException(getName() + " expects a commit message.");
    }

    String message = (String) top;

    top = stack.pop();

    if (!(top instanceof String)) {
      throw new WarpScriptException(getName() + " expects a relative file path.");
    }

    String path = (String) top;

    top = stack.pop();

    if (!(top instanceof String)) {
      throw new WarpScriptException(getName() + " expects a git repository name.");
    }

    String repo = (String) top;

    top = stack.pop();

    byte[] content = null;

    if (top instanceof String) {
      content = ((String) top).getBytes(StandardCharsets.UTF_8);
    } else if (top instanceof byte[]) {
      content = (byte[]) top;
    } else {
      throw new WarpScriptException(getName() + " can only store content of type STRING or BYTES.");
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

    if (repo.equals(capabilities.get(GitWarpScriptExtension.CAP_GITREPO))) {
      throw new WarpScriptException(getName() + " missing or invalid '" + GitWarpScriptExtension.CAP_GITREPO + "' capability.");
    }

    //
    // Create the directories needed for 'path' and store 'content' in path
    //

    if (path.contains("/../") || path.contains("/./") || path.startsWith("./") || path.startsWith("../")) {
      throw new WarpScriptException(getName() + " invalid path.");
    }

    File repodir = new File(GitWarpScriptExtension.getRoot(), repo);
    File target = new File(repodir, path);

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

    Git git = null;

    try {
      //
      // Synchronize on the interned named of the repo
      //

      git = Git.open(new File(GitWarpScriptExtension.getRoot(), repo));

      AddCommand add = git.add();
      add.addFilepattern(path);
      add.call();

      CommitCommand commit = git.commit();
      // Extract author and email from capabilities gituser and gitemail if set
      commit.setAuthor(capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITNAME, "warp10-ext-git"), capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITEMAIL, "contact@senx.io"));
      commit.setAllowEmpty(false);
      commit.setCommitter("warp10-ext-git", "contact@senx.io");
      commit.setMessage(message);

      // FIXME(hbs): synchronize on repo.intern()?
      RevCommit rev = commit.call();
      stack.push(rev.getId().name());
    } catch (Exception e) {
      throw new WarpScriptException(getName() + " error opening Git repository.", e);
    } finally {
      if (null != git) {
        git.close();
      }
    }

    return stack;
  }
}
