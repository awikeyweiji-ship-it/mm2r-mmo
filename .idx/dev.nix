{ pkgs, ... }: {
  channel = "unstable";

  packages = [
    pkgs.flutter
    pkgs.dart
    pkgs.nodejs_20
    pkgs.jdk17
  ];

  env = {
    BACKEND_PORT = "8080";
    JAVA_HOME = "${pkgs.jdk17}/lib/openjdk";
    # WEB_MODE: dev (flutter run, supports hot reload) OR release (flutter build, stable)
    WEB_MODE = "dev";
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
            "flutter pub get && if [ \"$WEB_MODE\" = \"release\" ]; then flutter build web --release --pwa-strategy=none --dart-define=BUILD_ID=$(date +%Y%m%d_%H%M%S) && cd tools && node web_dev_proxy.js; else export FRONTEND_PORT=5000; export RENDERER_URL=\"http://127.0.0.1:$FRONTEND_PORT\"; (cd tools && RENDERER_URL=$RENDERER_URL node web_dev_proxy.js) & flutter run -d web-server --web-hostname 127.0.0.1 --web-port $FRONTEND_PORT --no-pub; fi"
          ];
          manager = "web";
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
