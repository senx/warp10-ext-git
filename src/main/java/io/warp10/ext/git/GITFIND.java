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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.ext.capabilities.Capabilities;

public class GITFIND extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public GITFIND(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object top = stack.pop();

    if (!(top instanceof Map)) {
      throw new WarpScriptException(getName() + " expects a parameter MAP.");
    }

    Map<Object,Object> params = (Map<Object,Object>) top;

    if (null != params.get(GitWarpScriptExtension.PARAM_REGEXP) && !(params.get(GitWarpScriptExtension.PARAM_REGEXP) instanceof String)) {
      throw new WarpScriptException(getName() + " unset filter predicate under key '" + GitWarpScriptExtension.PARAM_REGEXP + "'.");
    }

    String filter = (String) params.get(GitWarpScriptExtension.PARAM_REGEXP);

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

    String subdir = capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR);

    if (null != subdir) {
      if (null == filter) {
        filter = subdir + "/.*";
      } else {
        if (filter.startsWith("^")) {
          filter = "^" + Pattern.quote(subdir) + "/" + filter.substring(1);
        } else {
          filter = Pattern.quote(subdir) + "/" + filter;
        }
      }
    }

    Git git = null;

    final String ffilter = filter;

    try {
      git = Git.open(new File(GitWarpScriptExtension.getRoot(), repo));

      // find the HEAD
      ObjectId lastCommitId = git.getRepository().resolve(Constants.HEAD);

      List<String> entries = new ArrayList<String>();

      RevWalk rwalk = new RevWalk(git.getRepository());
      RevCommit commit = rwalk.parseCommit(lastCommitId);
      RevTree tree = commit.getTree();
      TreeWalk twalk = new TreeWalk(git.getRepository());
      twalk.addTree(tree);
      twalk.setRecursive(true);
      twalk.setFilter(null == ffilter ? TreeFilter.ALL : new TreeFilter() {

        Matcher matcher = Pattern.compile(ffilter).matcher("");

        @Override
        public boolean shouldBeRecursive() { return true; }
        @Override
        public boolean include(TreeWalk walker) throws MissingObjectException, IncorrectObjectTypeException, IOException {
          if (walker.getRawPath()[walker.getPathLength() - 1] == '/') {
            return true;
          }

          if (walker.isSubtree()) {
            return true;
          }
          String path = walker.getPathString();

          return matcher.reset(path).matches();
        }

        @Override
        public TreeFilter clone() { return null; }
      });

      while(twalk.next()) {
        if (null != subdir) {
          entries.add(twalk.getPathString().substring(subdir.length() + 1));
        } else {
          entries.add(twalk.getPathString());
        }
      }

      twalk.close();
      rwalk.dispose();

      stack.push(entries);
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
