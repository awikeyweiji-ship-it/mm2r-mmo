{
  # See https://www.jetpack.io/devbox/docs/configuration/ for reference.
  pkgs: [
    pkgs.flutter.withPackages (ps: with ps; [ 
      # Add your flutter packages here
    ])
  ],
  # Installs these Nix packages into the environment.
  # pkgs: [
  #   pkgs.go
  #   pkgs.python311
  #   pkgs.python311Packages.pip
  # ],
  # Installs these VSCode extensions.
  # extensions: [
  #   "vscodevim.vim"
  #   "esbenp.prettier-vscode"
  # ],
  # Runs this command when the environment is created.
  init_hook: [
    "echo 'Welcome to Firebase Genkit!\n'"
    # Add your initialization commands here.
  ],
  # Runs this command when the user connects to the environment.
  on_create: [],
  # Runs this command when the user opens the editor.
  on_connect: [],
  dev_platforms: ["x86_64-linux"],
  idx: {
    previews: {
      enable: true
      previews: [
        {
          id: "backend"
          name: "Backend"
          port: 8080
          command: ["(cd server && npm start)"] # Corrected command
        }
        {
          id: "web"
          name: "Web"
          port: 8675
          command: ["npm", "run", "dev"]
        }
      ]
    }
    extensions: [
      "dart-code.dart-code"
      "dart-code.flutter"
      "ms-vscode.vscode-typescript-next"
    ]
  }
}
