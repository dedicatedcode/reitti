{
  maven,
  shortRev,
  time,
  artifactId,
  description,
  version,
  license,
  jdk,
  pkgs,
  lib,
  contributors-json,
}:
let
  jarName = "${artifactId}-app.jar";
in
maven.buildMavenPackage {
  pname = artifactId;
  inherit version;

  nativeBuildInputs = [
    pkgs.xmlstarlet
    pkgs.jq
  ];

  src = ./.;
  mvnHash = "sha256-zSufKXEs3kNPOubpjTMuhJwaMyXJ0lDkQ7sq143YvH4=";
  mvnJdk = jdk;

  mvnFlags = "-Dmaven.test.skip=true -DskipTests -Dmaven.gitcommitid.skip=true";

  postPatch = ''
    NS="http://maven.apache.org/POM/4.0.0"

    # Surgically remove plugins that interfere with the sandbox or force tests
    xmlstarlet ed -L -N x="$NS" \
      -d "//x:plugin[x:artifactId='git-commit-id-maven-plugin']" \
      -d "//x:plugin[x:artifactId='maven-surefire-plugin']" \
      -d "//x:plugin[x:artifactId='jacoco-maven-plugin']" \
      pom.xml

    # Physically remove test sources so Maven can't even "look" at them
    rm -rf src/test

    cat > src/main/resources/git.properties <<EOF
    git.commit.id.abbrev=${shortRev}
    git.commit.time=${time}
    git.build.time=${time}
    git.tags=nix-build-${shortRev}
    EOF

    # Transform GitHub API contributors to expected format
    jq '{
      contributors: [
        .[] |
        select(.type == "User") |
        select(.login != "dependabot[bot]") |
        select(.login != "github-actions[bot]") |
        select(.login != "weblate") |
        select(.login != "aider-chat-bot") |
        select(.login != "dgraf-gh") |
        {
          name: (.name // .login),
          role: "Contributor",
          avatar: .avatar_url,
          github: .login
        }
      ]
    }' ${contributors-json} > src/main/resources/contributors.json
  '';

  installPhase = ''
    runHook preInstall
    mkdir -p $out/share/java
    cp target/*.jar $out/share/java/${jarName}
    runHook postInstall
  '';

  meta = {
    inherit license description;
  };

  passthru = {
    jarPath = "share/java/${jarName}";
  };
}
