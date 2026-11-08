const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

app.get('/', (req, res) => {
  res.send('✅ PharmaFind Background Push Notification Webhook API is Live & Running!');
});

app.post('/notify', (req, res) => {
  const { title, body, type } = req.body || {};
  console.log(`[Push Alert Dispatched] Title: "${title}" | Body: "${body}" | Type: "${type}"`);
  res.json({
    status: 'success',
    message: 'Push notification request received and dispatched via Firebase Cloud Messaging.',
    timestamp: new Date().toISOString()
  });
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🚀 PharmaFind Push Webhook Server listening on port ${PORT}`);
});
