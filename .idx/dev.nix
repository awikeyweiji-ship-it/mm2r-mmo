{ pkgs, ... }: {
  channel = "unstable";

  packages = [
    pkgs.flutter
    pkgs.nodejs_20
    pkgs.jdk17
  ];

  env = {
    BACKEND_PORT = "8080";
    JAVA_HOME = "${pkgs.jdk17}/lib/openjdk";
    # WEB_MODE: 'release' (flutter build web, stable) OR 'dev' (flutter run, supports hot reload)
    WEB_MODE = "release";
  };

  idx = {
    extensions = [
      "dart-code.dart-code"
      "dart-code.flutter"
      "ms-vscode.vscode-typescript-next"
    ];

    workspace = {
      onCreate = {
        # Only install if missing to speed up rebuilds if volumes persist
        setup-server = "if [ ! -d \"server/node_modules\" ]; then cd server && npm install; fi";
        setup-tools = "if [ ! -d \"tools/node_modules\" ]; then cd tools && npm install; fi";
        setup-flutter = "flutter pub get"; 
        config-flutter = "flutter config --no-analytics";
      };
      onStart = {
        # Minimal startup actions
        config-flutter = "flutter config --no-analytics";
      };
    };

    previews = {
      enable = true;
      previews = {
        backend = {
          command = [
            "bash"
            "-lc"
            "if [ ! -d \"server/node_modules\" ]; then (cd server && npm install); fi && cd server && npm start"
          ];
          manager = "web";
          env = {
            PORT = "8080";
          };
        };
        web = {
          command = [
            "bash"
            "-lc"
            "export PORT=\"$PORT\"; echo \"IDX PORT=$PORT\"; if [ ! -d \"tools/node_modules\" ]; then (cd tools && npm install); fi && if [ \"$WEB_MODE\" = \"release\" ]; then flutter build web --release --pwa-strategy=none --dart-define=BUILD_ID=$(date +%Y%m%d_%H%M%S) && cd tools && node web_dev_proxy.js; else export FRONTEND_PORT=5000; export RENDERER_URL=\"http://127.0.0.1:$FRONTEND_PORT\"; (cd tools && RENDERER_URL=$RENDERER_URL node web_dev_proxy.js) & flutter run -d web-server --web-hostname 127.0.0.1 --web-port $FRONTEND_PORT --no-pub; fi"
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
      };
    };
  };
}
