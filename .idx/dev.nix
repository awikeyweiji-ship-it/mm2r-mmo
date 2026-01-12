{ pkgs, ... }: {
  channel = "stable-23.11"; # or "unstable"

  packages = [
    pkgs.flutter
    pkgs.dart
    pkgs.nodejs_20
  ];

  env = {
    # Environment variables if needed
  };

  idx = {
    # Search for the extensions you want on https://open-vsx.org/ and use "publisher.id".
    extensions = [
      "dart-code.dart-code"
      "dart-code.flutter"
      "ms-vscode.vscode-typescript-next"
    ];

    # Workspace lifecycle hooks
    workspace = {
      # Runs when a workspace is first created
      onCreate = {
        # Example: install JS dependencies
        # npm-install = "npm install";
      };
      # Runs when the workspace is (re)started
      onStart = {
        # Example: start a background task
      };
    };

    previews = {
      enable = true;
      previews = {
        web = {
          command = [
            "flutter"
            "run"
            "-d"
            "web-server"
            "--web-hostname"
            "0.0.0.0"
            "--web-port"
            "$PORT"
          ];
          manager = "flutter";
        };
        backend = {
          command = [
            "bash"
            "-c"
            "cd server && npm install && npm start"
          ];
          env = {
            PORT = "8080";
          };
        };
      };
    };
  };
}
