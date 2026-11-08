const express = require('express');
const cors = require('cors');
const admin = require('firebase-admin');

const app = express();
app.use(cors());
app.use(express.json());

// Initialize Firebase Admin SDK if service account environment variable is provided
let isFirebaseInitialized = false;
try {
  if (process.env.FIREBASE_SERVICE_ACCOUNT) {
    const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    isFirebaseInitialized = true;
    console.log('✅ Firebase Admin SDK Initialized Successfully!');
  } else {
    console.log('⚠️ FIREBASE_SERVICE_ACCOUNT environment variable not set. Logs only mode.');
  }
} catch (err) {
  console.error('❌ Error initializing Firebase Admin SDK:', err.message);
}

app.get('/', (req, res) => {
  res.send('✅ PharmaFind Background Push Notification Webhook API is Live & Running!');
});

app.post('/notify', async (req, res) => {
  const { title, body, type, sender_id, sender_role } = req.body || {};
  console.log(`[Push Alert Triggered] Title: "${title}" | Body: "${body}" | Type: "${type}" | Sender: ${sender_role} #${sender_id}`);

  if (isFirebaseInitialized) {
    try {
      const message = {
        notification: {
          title: title || 'PharmaFind Alert',
          body: body || 'You have a new update.'
        },
        data: {
          title: title || 'PharmaFind Alert',
          body: body || 'You have a new update.',
          type: type || 'general',
          sender_id: String(sender_id || '-1'),
          sender_role: String(sender_role || '')
        },
        topic: 'pharmafind_all'
      };

      const response = await admin.messaging().send(message);
      console.log('🚀 FCM Push Notification Sent Successfully:', response);
      return res.json({
        status: 'success',
        fcmResponse: response,
        timestamp: new Date().toISOString()
      });
    } catch (fcmError) {
      console.error('❌ Error sending FCM push message:', fcmError.message);
      return res.status(500).json({
        status: 'error',
        error: fcmError.message
      });
    }
  } else {
    return res.json({
      status: 'warning',
      message: 'Trigger logged. To send actual FCM push, set FIREBASE_SERVICE_ACCOUNT on Render dashboard.',
      timestamp: new Date().toISOString()
    });
  }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`🚀 PharmaFind Push Webhook Server listening on port ${PORT}`);
});
