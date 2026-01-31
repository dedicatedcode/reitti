{
  reitti,
  reittiImageName,
  reittiImageTag,
  jdk,
  pkgs,
  self,
  license,
  gitInfo,
}:
let
  DATA_DIR = "/data";
  APP_HOME = "/app";
  TMP_DIR = "/tmp";
  description = "Reitti - Personal Location Tracking & Analysis";
in
{
  reittiImage = pkgs.dockerTools.buildLayeredImage {
    name = reittiImageName;
    tag = reittiImageTag;
    contents = [
      jdk
      pkgs.busybox
    ];

    enableFakechroot = true;
    fakeRootCommands = ''
      mkdir -p ${APP_HOME} ${DATA_DIR} ${TMP_DIR}

      cp ${reitti}/${reitti.jarPath} ${APP_HOME}/reitti-app.jar

      # User/Group Setup (UID 1000)
      mkdir -p ./etc
      echo "reitti:x:1000:1000:reitti:/home/reitti:/bin/sh" > ./etc/passwd
      echo "reitti:x:1000:" > ./etc/group

      # Berechtigungen setzen
      chown -R 1000:1000 ${APP_HOME} ${DATA_DIR} ${TMP_DIR}
    '';

    created = gitInfo.formattedTime;

    config = {
      User = "reitti";
      ExposedPorts = {
        "8080/tcp" = { };
      };
      Cmd = [
        "${jdk}/bin/java"
        "-Djava.io.tmpdir=${TMP_DIR}"
        "-jar"
        "reitti-app.jar"
      ];
      WorkingDir = APP_HOME;
      Labels = {
        "maintainer" = "dedicatedcode";
        "org.opencontainers.image.source" = "https://github.com/dedicatedcode/reitti";
        "org.opencontainers.image.description" = description;
        "org.opencontainers.image.licenses" = license.shortName;
      };
      Healthcheck = {
        Test = [
          "CMD-SHELL"
          "wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1"
        ];
        Interval = 5000000000; # 5s in nanos
        Timeout = 3000000000; # 3s
        Retries = 20;
        StartPeriod = 1000000000; # 1s
      };
      Env = [
        "SPRING_PROFILES_ACTIVE=docker"
        "APP_HOME=${APP_HOME}"
        "DATA_DIR=${DATA_DIR}"
      ];
    };
  };
}
