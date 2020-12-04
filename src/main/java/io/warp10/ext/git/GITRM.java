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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.revwalk.RevCommit;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.warp.sdk.Capabilities;

public class GITRM extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public GITRM(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object top = stack.pop();

    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + " expects a parameter MAP.");
    }

    Map<Object,Object> params = (Map<Object,Object>) top;

    List<String> pathes = new ArrayList<String>();

    if (params.get(GitWarpScriptExtension.PARAM_PATH) instanceof String) {
      pathes.add((String) params.get(GitWarpScriptExtension.PARAM_PATH));
    } else if (params.get(GitWarpScriptExtension.PARAM_PATH) instanceof List) {
      for (Object elt: (List) params.get(GitWarpScriptExtension.PARAM_PATH)) {
        if (!(elt instanceof String)) {
          throw new WarpScriptException(getName() + " key '" + GitWarpScriptExtension.PARAM_PATH + "' should point to a path or a list thereof.");
        }
        pathes.add((String) elt);
      }
    } else {
      throw new WarpScriptException(getName() + " key '" + GitWarpScriptExtension.PARAM_PATH + "' should point to a path or a list thereof.");
    }

    if (!(params.get(GitWarpScriptExtension.PARAM_REPO) instanceof String)) {
      throw new WarpScriptException(getName() + " unset repository under key '" + GitWarpScriptExtension.PARAM_REPO + "'.");
    }

    String repo = (String) params.get(GitWarpScriptExtension.PARAM_REPO);

    if (!(params.get(GitWarpScriptExtension.PARAM_MESSAGE) instanceof String)) {
      throw new WarpScriptException(getName() + " expects a commit message under key '" + GitWarpScriptExtension.PARAM_MESSAGE + "'.");
    }

    String message = (String) params.get(GitWarpScriptExtension.PARAM_MESSAGE);

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

    String subdir = capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR);

    Git git = null;

    try {
      git = Git.open(new File(GitWarpScriptExtension.getRoot(), repo));

      RmCommand rm = git.rm();
      for (String path: pathes) {
        if (null == subdir) {
          rm.addFilepattern(path);
        } else {
          rm.addFilepattern(subdir + "/" + path);
        }
      }

      rm.call();

      CommitCommand commit = git.commit();
      // Extract author and email from capabilities gituser and gitemail if set
      commit.setAuthor(capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITNAME, "warp10-ext-git"), capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITEMAIL, "contact@senx.io"));
      commit.setAllowEmpty(false);
      commit.setCommitter("warp10-ext-git", "contact@senx.io");
      commit.setMessage(message);
      RevCommit rev = commit.call();
      stack.push(rev.getId().name());
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
