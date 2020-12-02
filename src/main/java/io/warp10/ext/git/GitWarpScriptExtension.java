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
import java.util.HashMap;
import java.util.Map;

import io.warp10.WarpConfig;
import io.warp10.warp.sdk.WarpScriptExtension;

public class GitWarpScriptExtension extends WarpScriptExtension {

  private static Map<String,Object> functions;

  /**
   * Root directory where all allowed repos will reside, possibly as symbolic links
   * to actual location.
   */
  public static final String CONF_ROOT = "git.root";

  public static final String GITLOAD = "GITLOAD";
  public static final String GITSTORE = "GITSTORE";
  public static final String GITRM = "GITRM";
  public static final String GITFIND = "GITFIND";
  public static final String GITLOG = "GITLOG";
  public static final String GITTAG = "GITTAG";

  /**
   * Repository the token can access
   */
  public static final String CAP_GITREPO = "git.repo";
  /**
   * The name to use for commits
   */
  public static final String CAP_GITNAME = "git.name";
  /**
   * The email address to use for commits
   */
  public static final String CAP_GITEMAIL = "git.email";

  /**
   * The subdirectory of 'git.repo' the token can access
   */
  public static final String CAP_GITSUBDIR = "git.subdir";

  /**
   * If this capability is present, any operation which
   * modifies the repository will not be allowed, access
   * will be (r)ead (o)nly (ro).
   */
  public static final String CAP_GITRO = "git.ro";

  public static final String PARAM_REPO = "repo";
  public static final String PARAM_MESSAGE = "message";
  public static final String PARAM_REGEXP = "regexp";
  public static final String PARAM_PATH = "path";
  public static final String PARAM_CONTENT = "content";
  public static final String PARAM_REV = "rev";
  public static final String PARAM_COUNT = "count";
  public static final String PARAM_SKIP = "skip";
  public static final String PARAM_TAG = "tag";
  public static final String PARAM_FORCE = "force";
  public static final String PARAM_WORKDIR = "workdir";

  public static final String INFOS_REV = "rev";
  public static final String INFOS_MESSAGE = "message";
  public static final String INFOS_AUTHOR_NAME = "author.name";
  public static final String INFOS_AUTHOR_EMAIL = "author.email";
  public static final String INFOS_AUTHOR_TIMESTAMP = "author.timestamp";
  public static final String INFOS_COMMITTER_NAME = "committer.name";
  public static final String INFOS_COMMITTER_EMAIL = "committer.email";
  public static final String INFOS_COMMITTER_TIMESTAMP = "committer.timestamp";
  public static final String INFOS_TYPE = "type";
  public static final String INFOS_TAG = "tag";
  public static final String INFOS_TAGGED = "tagged";
  public static final String INFOS_TAGS = "tags";

  private static final File ROOT;

  static {

    String root = WarpConfig.getProperty(CONF_ROOT);

    if (null != root) {
      File froot = new File(root);
      if (!froot.exists()) {
        throw new RuntimeException("Configured '" + CONF_ROOT + "' (" + root + ") does not exist.");
      }
      ROOT = froot;
    } else {
      ROOT = null;
    }

    functions = new HashMap<String,Object>();

    functions.put(GITLOAD, new GITLOAD(GITLOAD));
    functions.put(GITSTORE, new GITSTORE(GITSTORE));
    functions.put(GITRM, new GITRM(GITRM));
    functions.put(GITFIND, new GITFIND(GITFIND));
    functions.put(GITLOG, new GITLOG(GITLOG));
    functions.put(GITTAG, new GITTAG(GITTAG));
  }

  @Override
  public Map<String, Object> getFunctions() {
    return functions;
  }

  public static File getRoot() {
    return ROOT;
  }
}
