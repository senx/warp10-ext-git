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
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.ext.capabilities.Capabilities;;

public class GITLOAD extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public GITLOAD(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object top = stack.pop();

    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + " expects a parameter MAP.");
    }

    Map<Object,Object> params = (Map<Object,Object>) top;

    if (!(params.get(GitWarpScriptExtension.PARAM_PATH) instanceof String)) {
      throw new WarpScriptException(getName() + " unset path under key '" + GitWarpScriptExtension.PARAM_PATH + "'.");
    }

    String path = (String) params.get(GitWarpScriptExtension.PARAM_PATH);

    if (!(params.get(GitWarpScriptExtension.PARAM_REPO) instanceof String)) {
      throw new WarpScriptException(getName() + " unset repository under key '" + GitWarpScriptExtension.PARAM_REPO + "'.");
    }

    String repo = (String) params.get(GitWarpScriptExtension.PARAM_REPO);

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

    //
    // Add git.subdir prefix to path if defined
    //

    String rawpath = path;

    if (null != capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR)) {
      path = capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR) + "/" + path;
    }

    if (path.contains("/../") || path.contains("/./") || path.startsWith("./") || path.startsWith("../")) {
      throw new WarpScriptException(getName() + " invalid path.");
    }

    File repodir = new File(GitWarpScriptExtension.getRoot(), repo);
    File target = new File(repodir, path);

//    if (!target.exists() || !target.isFile() || ".git".equals(path)) {
//      throw new WarpScriptException("Invalid path '" + rawpath + "'.");
//    }

    Git git = null;

    try {
      git = Git.open(new File(GitWarpScriptExtension.getRoot(), repo));

      // find the HEAD
      // TODO(hbs): support branches/tags
      ObjectId lastCommitId = git.getRepository().resolve(Constants.HEAD);

      RevWalk rwalk = new RevWalk(git.getRepository());
      RevCommit commit = rwalk.parseCommit(lastCommitId);
      RevTree tree = commit.getTree();
      TreeWalk twalk = new TreeWalk(git.getRepository());
      twalk.addTree(tree);
      twalk.setRecursive(true);
      twalk.setFilter(PathFilter.create(path));

      if (twalk.next()) {
        ObjectId objectId = twalk.getObjectId(0);
        ObjectLoader loader = git.getRepository().open(objectId);

        stack.push(loader.getBytes());
      } else {
        stack.push(null);
      }

      twalk.close();
      rwalk.dispose();
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
