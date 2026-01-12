{ pkgs, ... }: {
  channel = "stable-23.11";

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
      };
      onStart = {
        # 可以选择在这里启动后端，或者放在 previews 里
      };
    };

    previews = {
      enable = true;
      previews = {
        # 1. 初始的 Web 界面
        web = {
          command = [
            "flutter"
            "run"
            "--machine"
            "-d"
            "web-server"
            "--web-hostname"
            "0.0.0.0"
            "--web-port"
            "$PORT"
          ];
          manager = "flutter";
        };
        # 2. 初始的 Android 界面 (需要 IDX 环境支持 Android Emulator)
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
        # 3. 后端服务
        backend = {
          command = [
            "bash"
            "-c"
            "cd server && npm install && HOST=0.0.0.0 PORT=8080 node src/index.js"
          ];
          env = {
            PORT = "8080";
          };
        };
      };
    };
  };
}
