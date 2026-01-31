{
  # Known Limitations:
  # - maven git commit id plugin cannot be used during nix builds
  # - generated docker image does not support custom uid/gids
  # - result is not fully reproducible as we grep the contributors list from the github api,
  #   this api does not support contributors at given commit hash, thus this will always be the newest contributors list
  description = "Reitti - Personal Location Tracking & Analysis";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?ref=nixos-unstable";
    utils.url = "github:numtide/flake-utils";
    treefmt-nix = {
      url = "github:numtide/treefmt-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      self,
      nixpkgs,
      utils,
      treefmt-nix,
    }:
    utils.lib.eachDefaultSystem (
      system:
      let
        contributors-json = builtins.fetchurl "https://api.github.com/repos/dedicatedcode/reitti/contributors";
        pkgs = import nixpkgs { inherit system; };
        jdk = pkgs.jdk25;
        license = pkgs.lib.licenses.mit; # TODO: read from pom?

        gitInfo = {
          shortRev = self.shortRev or "${self.dirtyShortRev or "unknown"}";
          rev = self.rev or "${self.dirtyRev or "unknown"}";
          formattedTime =
            let
              sub = from: len: builtins.substring from len self.lastModifiedDate;
              year = sub 0 4;
              month = sub 4 2;
              day = sub 6 2;
              hour = sub 8 2;
              minute = sub 10 2;
              second = sub 12 2;
            in
            "${year}-${month}-${day}T${hour}:${minute}:${second}Z";
        };

        pomJson = pkgs.runCommand "pom-json" { nativeBuildInputs = [ pkgs.yq-go ]; } ''
          yq -p xml -o json . ${./pom.xml} > $out
        '';
        pomData = builtins.fromJSON (builtins.readFile pomJson);
        metadata = {
          artifactId = pomData.project.artifactId or "unknown-project";
          description = pomData.project.description or "No description provided";
          version = pomData.project.version or "unknown-version";
        };

        jar = pkgs.callPackage ./reitti.nix {
          inherit (pkgs) maven lib;
          inherit (gitInfo) shortRev;
          time = gitInfo.formattedTime;
          inherit (metadata) artifactId description version;
          inherit jdk license contributors-json;
        };

        dockerImage = pkgs.callPackage ./reitti.docker.nix {
          reitti = jar;
          reittiImageName = "reitti";
          reittiImageTag = "latest";
          inherit
            jdk
            pkgs
            self
            license
            gitInfo
            ;
        };

        formatting =
          let
            treefmtEval = treefmt-nix.lib.evalModule pkgs (
              { pkgs, ... }:
              {
                projectRootFile = "flake.nix";
                programs.nixfmt.enable = true;
              }
            );
          in
          {
            formatter = treefmtEval.config.build.wrapper;
          };
      in
      {
        inherit (formatting) formatter;

        devShells.default = pkgs.mkShell {
          packages = [
            jdk
            pkgs.maven
            pkgs.jq
            pkgs.curl
            pkgs.git
          ];
          JAVA_HOME = jdk;
          shellHook = ''
            export PATH="${jdk}/bin:${pkgs.maven}/bin:$PATH"
          '';
        };

        packages = {
          inherit jar;
          docker = dockerImage.reittiImage;
          default = self.packages.${system}.jar;
        };
      }
    );
}
