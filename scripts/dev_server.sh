#!/bin/bash
# Starts the backend server
cd server
if [ ! -d "node_modules" ]; then
  echo "Installing server dependencies..."
  npm install
fi
echo "Starting server..."
npm run dev
