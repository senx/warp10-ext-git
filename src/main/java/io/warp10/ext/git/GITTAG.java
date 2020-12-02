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
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.api.TagCommand;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.ext.capabilities.Capabilities;;

public class GITTAG extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public GITTAG(String name) {
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

    if (!(params.get(GitWarpScriptExtension.PARAM_TAG) instanceof String)) {
      throw new WarpScriptException(getName() + " unset tag under key '" + GitWarpScriptExtension.PARAM_TAG + "'.");
    }

    String tag = (String) params.get(GitWarpScriptExtension.PARAM_TAG);

    boolean force = Boolean.TRUE.equals(params.get(GitWarpScriptExtension.PARAM_FORCE));

    String rev = Constants.HEAD;

    if (params.get(GitWarpScriptExtension.PARAM_REV) instanceof String) {
      rev = (String) params.get(GitWarpScriptExtension.PARAM_REV);
    }

    if (!(params.get(GitWarpScriptExtension.PARAM_REPO) instanceof String)) {
      throw new WarpScriptException(getName() + " unset repository under key '" + GitWarpScriptExtension.PARAM_REPO + "'.");
    }

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

    if (null != capabilities.get(GitWarpScriptExtension.CAP_GITRO)) {
      throw new WarpScriptException(getName() + " no right to modify repository '" + repo + "'.");
    }

    //
    // Set git.subdir prefix as path to tag
    //

    String path = null;

    if (null != capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR)) {
      path = capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR);
    }

    Git git = null;

    try {
      git = Git.open(new File(GitWarpScriptExtension.getRoot(), repo));

      // If capability 'git.subdir' is set, determine the first revision before the provided one which affected 'git.subdir'
      RevCommit revcommit = null;

      if (null != path) {
        LogCommand log = git.log();
        ObjectId oid = git.getRepository().resolve(rev);
        log.add(oid);
        log.addPath(path);

        for (RevCommit rc: log.call()) {
          revcommit = rc;
          break;
        }

        if (null == revcommit) {
          throw new WarpScriptException(getName() + " invalid revision '" + rev + "', be more specific via key '" + GitWarpScriptExtension.PARAM_REV + "'.");
        }
      } else {
        RevWalk rwalk = new RevWalk(git.getRepository());
        revcommit = rwalk.parseCommit(git.getRepository().resolve(rev));
      }

      TagCommand tc = git.tag();
      tc.setForceUpdate(force);
      tc.setAnnotated(true);
      tc.setName(tag);
      tc.setMessage(message);
      tc.setObjectId(revcommit);
      PersonIdent person = new PersonIdent(capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITNAME, "warp10-ext-git"), capabilities.getOrDefault(GitWarpScriptExtension.CAP_GITEMAIL, "contact@senx.io"));
      tc.setTagger(person);

      stack.push(tc.call().getName());
    } catch (Exception e) {
      // Do not include original exception so we do not leak internal path
      throw new WarpScriptException(getName() + " error adding tag '" + tag + "' to repository  '" + repo + "'.");
    } finally {
      if (null != git) {
        git.close();
      }
    }

    return stack;
  }
}
