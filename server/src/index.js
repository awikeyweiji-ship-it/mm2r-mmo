const express = require('express');
const app = express();
const port = process.env.PORT || 8080;

app.get('/health', (req, res) => {
  res.json({
    ok: true,
    service: 'gateway',
    ts: new Date().toISOString()
  });
});

// TODO: Implement WebSocket support
// const server = require('http').createServer(app);
// const { Server } = require('socket.io');
// const io = new Server(server);

app.listen(port, () => {
  console.log(`Gateway service listening on port ${port}`);
});
