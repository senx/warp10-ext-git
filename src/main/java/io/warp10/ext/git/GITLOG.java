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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.LogCommand;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

import io.warp10.continuum.store.Constants;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.ext.capabilities.Capabilities;;

public class GITLOG extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public GITLOG(String name) {
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

    Integer count = null;
    Integer skip = null;

    if (params.get(GitWarpScriptExtension.PARAM_COUNT) instanceof Long) {
      count = Math.toIntExact(((Long) params.get(GitWarpScriptExtension.PARAM_COUNT)).longValue());
      if (count < 0) {
        count = 0;
      }
    }

    if (params.get(GitWarpScriptExtension.PARAM_SKIP) instanceof Long) {
      skip = Math.toIntExact(((Long) params.get(GitWarpScriptExtension.PARAM_SKIP)).longValue());
      if (skip < 0) {
        skip = null;
      }
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

    String subdir = capabilities.get(GitWarpScriptExtension.CAP_GITSUBDIR);

    Git git = null;

    try {
      git = Git.open(new File(GitWarpScriptExtension.getRoot(), repo));

      Map<String,Map<Object,Object>> revs = new LinkedHashMap<String,Map<Object,Object>>();

      LogCommand log = git.log();

      if (null != count) {
        log = log.setMaxCount(count);
      }
      if (null != skip) {
        log = log.setSkip(skip);
      }

      for (String path: pathes) {
        if (null == subdir) {
          log.addPath(path);
        } else {
          log.addPath(subdir + "/" + path);
        }
      }

      Iterable<RevCommit> commits = log.call();

      for (RevCommit commit: commits) {
        Map<Object,Object> infos = new LinkedHashMap<Object,Object>();

        infos.put(GitWarpScriptExtension.INFOS_REV, commit.getName());
        infos.put(GitWarpScriptExtension.INFOS_MESSAGE, commit.getFullMessage());
        infos.put(GitWarpScriptExtension.INFOS_TYPE, org.eclipse.jgit.lib.Constants.typeString(commit.getType()));

        PersonIdent person = commit.getAuthorIdent();
        infos.put(GitWarpScriptExtension.INFOS_AUTHOR_NAME, person.getName());
        infos.put(GitWarpScriptExtension.INFOS_AUTHOR_EMAIL, person.getEmailAddress());
        infos.put(GitWarpScriptExtension.INFOS_AUTHOR_TIMESTAMP, person.getWhen().getTime() * Constants.TIME_UNITS_PER_MS);

        person = commit.getCommitterIdent();
        infos.put(GitWarpScriptExtension.INFOS_COMMITTER_NAME, person.getName());
        infos.put(GitWarpScriptExtension.INFOS_COMMITTER_EMAIL, person.getEmailAddress());
        infos.put(GitWarpScriptExtension.INFOS_COMMITTER_TIMESTAMP, person.getWhen().getTime() * Constants.TIME_UNITS_PER_MS);

        revs.put(commit.getName(), infos);
      }

      // Retrieve tags
      List<Ref> tagrefs = git.tagList().call();
      RevWalk walk = new RevWalk(git.getRepository());
      for (Ref ref: tagrefs) {
        RevObject rev = walk.parseAny(ref.getObjectId());
        if (rev instanceof RevTag) {
          RevTag rt = (RevTag) rev;
          Map<Object,Object> infos = new LinkedHashMap<Object,Object>();

          infos.put(GitWarpScriptExtension.INFOS_REV, rt.getName());
          infos.put(GitWarpScriptExtension.INFOS_MESSAGE, rt.getFullMessage());
          infos.put(GitWarpScriptExtension.INFOS_TYPE, org.eclipse.jgit.lib.Constants.typeString(rt.getType()));
          infos.put(GitWarpScriptExtension.INFOS_TAG, rt.getTagName());
          infos.put(GitWarpScriptExtension.INFOS_TAGGED, rt.getObject().getName());

          PersonIdent person = rt.getTaggerIdent();
          infos.put(GitWarpScriptExtension.INFOS_AUTHOR_NAME, person.getName());
          infos.put(GitWarpScriptExtension.INFOS_AUTHOR_EMAIL, person.getEmailAddress());
          infos.put(GitWarpScriptExtension.INFOS_AUTHOR_TIMESTAMP, person.getWhen().getTime() * Constants.TIME_UNITS_PER_MS);

          // Only store tags for revisions already in 'revs'
          if (revs.containsKey(rt.getObject().getName())) {
            revs.put(rt.getName(), infos);
            infos = revs.get(rt.getObject().getName());
            List<String> tags = (List<String>) infos.get(GitWarpScriptExtension.INFOS_TAGS);
            if (null == tags) {
              tags = new ArrayList<String>();
              infos.put(GitWarpScriptExtension.INFOS_TAGS, tags);
            }
            tags.add(rt.getTagName());
          }
        } else if (rev instanceof RevCommit) {
          RevCommit commit = (RevCommit) rev;
          Map<Object,Object> infos = new LinkedHashMap<Object,Object>();

          infos.put(GitWarpScriptExtension.INFOS_REV, commit.getName());
          infos.put(GitWarpScriptExtension.INFOS_MESSAGE, commit.getFullMessage());
          infos.put(GitWarpScriptExtension.INFOS_TYPE, org.eclipse.jgit.lib.Constants.typeString(commit.getType()));

          PersonIdent person = commit.getAuthorIdent();
          infos.put(GitWarpScriptExtension.INFOS_AUTHOR_NAME, person.getName());
          infos.put(GitWarpScriptExtension.INFOS_AUTHOR_EMAIL, person.getEmailAddress());
          infos.put(GitWarpScriptExtension.INFOS_AUTHOR_TIMESTAMP, person.getWhen().getTime() * Constants.TIME_UNITS_PER_MS);

          person = commit.getCommitterIdent();
          infos.put(GitWarpScriptExtension.INFOS_COMMITTER_NAME, person.getName());
          infos.put(GitWarpScriptExtension.INFOS_COMMITTER_EMAIL, person.getEmailAddress());
          infos.put(GitWarpScriptExtension.INFOS_COMMITTER_TIMESTAMP, person.getWhen().getTime() * Constants.TIME_UNITS_PER_MS);

          revs.putIfAbsent(commit.getName(), infos);
        } else {
          throw new WarpScriptException(getName() + " encountered an invalid tag reference.");
        }
      }

      stack.push(new ArrayList<Object>(revs.values()));
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
