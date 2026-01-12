{ pkgs, ... }: {
  channel = "unstable";

  packages = [
    pkgs.flutter
    pkgs.dart
    pkgs.nodejs_20
  ];

  env = {
    BACKEND_PORT = "8080";
  };

  idx = {
    extensions = [
      "dart-code.dart-code"
      "dart-code.flutter"
      "ms-vscode.vscode-typescript-next"
    ];

    workspace = {
      onCreate = {
        npm-install = "cd server && npm install";
        flutter-pub-get = "flutter pub get";
        flutter-config = "flutter config --no-analytics";
      };
      onStart = {
        flutter-config = "flutter config --no-analytics";
      };
    };

    previews = {
      enable = true;
      previews = {
        web = {
          command = [
            "bash"
            "-lc"
            "flutter pub get && flutter config --no-analytics && flutter run -d web-server --web-hostname 0.0.0.0 --web-port $PORT"
          ];
          manager = "flutter";
        };
        android = {
          command = [
            "flutter"
            "run"
            "--machine"
            "-d"
            "android"
            "-d"
            "emulator-5554"
          ];
          manager = "flutter";
        };
        backend = {
          command = [
            "bash"
            "-c"
            "cd server && npm install && HOST=0.0.0.0 PORT=8080 node src/index.js"
          ];
          env = {
            PORT = "8080";
          };
          manager = "web";
        };
      };
    };
  };
}
