const SHEET_NAME = 'Messages';
const HEADERS = [
  'seq', 'kind', 'window_start', 'window_end', 'device', 'uploaded_at', 'v',
  'count', 'masked', 'skipped_otp', 'msg_id', 'received_at', 'sender', 'sim',
  'body', 'redacted'
];
const MAX_SKEW_SECONDS = 300;
const MAX_BODY_BYTES = 1024 * 1024;

function setup() {
  const props = PropertiesService.getScriptProperties();
  let secret = props.getProperty('SECRET');
  if (!secret) {
    secret = Utilities.getUuid().replace(/-/g, '') + Utilities.getUuid().replace(/-/g, '');
    props.setProperty('SECRET', secret);
  }
  let id = props.getProperty('SPREADSHEET_ID');
  if (!id) {
    const ss = SpreadsheetApp.create('Hark SMS Bridge');
    id = ss.getId();
    props.setProperty('SPREADSHEET_ID', id);
    ensureSheet(ss);
  }
  Logger.log('Shared secret: ' + secret);
  Logger.log('Spreadsheet: ' + SpreadsheetApp.openById(id).getUrl());
  Logger.log('Deploy: Deploy > New deployment > Web app.');
  Logger.log('Execute as: Me. Who has access: Anyone.');
}

function doGet() {
  return jsonOut({ ok: true, service: 'hark-sms-bridge', version: 1 });
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    if (!e || !e.postData || !e.postData.contents) {
      return jsonOut({ ok: false, error: 'empty request' });
    }
    if (e.postData.contents.length > MAX_BODY_BYTES) {
      return jsonOut({ ok: false, error: 'request too large' });
    }

    const props = PropertiesService.getScriptProperties();
    const secret = props.getProperty('SECRET');
    const spreadsheetId = props.getProperty('SPREADSHEET_ID');
    if (!secret || !spreadsheetId) {
      return jsonOut({ ok: false, error: 'server not configured, run setup' });
    }

    let envelope;
    try {
      envelope = JSON.parse(e.postData.contents);
    } catch (err) {
      return jsonOut({ ok: false, error: 'invalid envelope' });
    }

    const body = envelope && typeof envelope.body === 'string' ? envelope.body : '';
    const timestamp = Number(envelope && envelope.ts);
    const signature = envelope && typeof envelope.sig === 'string' ? envelope.sig : '';
    if (!body || !isFinite(timestamp) || !signature) {
      return jsonOut({ ok: false, error: 'invalid envelope' });
    }

    const nowSeconds = Math.floor(Date.now() / 1000);
    if (Math.abs(nowSeconds - timestamp) > MAX_SKEW_SECONDS) {
      return jsonOut({ ok: false, error: 'stale or future timestamp' });
    }

    const expected = hmacHex(secret, timestamp + '.' + body);
    if (!secureEquals(expected, signature)) {
      return jsonOut({ ok: false, error: 'signature rejected' });
    }

    let payload;
    try {
      payload = JSON.parse(body);
    } catch (err) {
      return jsonOut({ ok: false, error: 'invalid body' });
    }

    if (payload && payload.test === true) {
      return jsonOut({ ok: true, uploaded_at: istNow(), message: 'connection ok' });
    }

    if (!payload || payload.kind !== 'batch') {
      return jsonOut({ ok: false, error: 'unsupported kind' });
    }

    const seq = toInt(payload.seq);
    if (seq < 1) {
      return jsonOut({ ok: false, error: 'invalid seq' });
    }

    lock.waitLock(15000);
    const sheet = ensureSheet(SpreadsheetApp.openById(spreadsheetId));
    const existing = findSeq(sheet, seq);
    if (existing) {
      return jsonOut({ ok: true, duplicate: true, uploaded_at: existing.uploadedAt });
    }

    const uploadedAt = istNow();
    const rows = buildRows(payload, seq, uploadedAt);
    const startRow = sheet.getLastRow() + 1;
    sheet.getRange(startRow, 1, rows.length, HEADERS.length).setValues(rows);

    return jsonOut({ ok: true, duplicate: false, uploaded_at: uploadedAt, rows: rows.length });
  } catch (err) {
    return jsonOut({ ok: false, error: 'server error: ' + (err && err.message ? err.message : err) });
  } finally {
    if (lock.hasLock()) {
      lock.releaseLock();
    }
  }
}

function buildRows(payload, seq, uploadedAt) {
  const base = [
    seq,
    'batch',
    safeText(payload.window_start),
    safeText(payload.window_end),
    safeText(payload.device),
    uploadedAt,
    toInt(payload.v),
    toInt(payload.count),
    toInt(payload.masked),
    toInt(payload.skipped_otp)
  ];

  const messages = Array.isArray(payload.messages) ? payload.messages : [];
  if (messages.length === 0) {
    return [base.concat(['', '', '', -1, '', 0])];
  }

  return messages.map(function (message) {
    message = message || {};
    return base.concat([
      safeText(message.msg_id),
      safeText(message.received_at),
      safeText(message.sender),
      toInt(message.sim),
      safeText(message.body),
      toInt(message.redacted)
    ]);
  });
}

function ensureSheet(spreadsheet) {
  let sheet = spreadsheet.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = spreadsheet.insertSheet(SHEET_NAME);
  }
  if (sheet.getLastRow() === 0) {
    sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
    sheet.getRange(1, 1, 1, HEADERS.length).setFontWeight('bold');
    sheet.setFrozenRows(1);
  }
  return sheet;
}

function findSeq(sheet, seq) {
  const lastRow = sheet.getLastRow();
  if (lastRow < 2) {
    return null;
  }
  const values = sheet.getRange(2, 1, lastRow - 1, 6).getValues();
  for (let i = 0; i < values.length; i++) {
    if (Number(values[i][0]) === seq) {
      return { uploadedAt: String(values[i][5] || '') };
    }
  }
  return null;
}

function safeText(value) {
  if (value === null || value === undefined) {
    return '';
  }
  const text = String(value);
  if (/^[=+\-@\t\r]/.test(text)) {
    return "'" + text;
  }
  return text;
}

function toInt(value) {
  const number = Number(value);
  return isFinite(number) ? Math.trunc(number) : -1;
}

function hmacHex(secret, message) {
  const bytes = Utilities.computeHmacSha256Signature(message, secret);
  const out = [];
  for (let i = 0; i < bytes.length; i++) {
    const value = bytes[i] < 0 ? bytes[i] + 256 : bytes[i];
    out.push(value.toString(16).padStart(2, '0'));
  }
  return out.join('');
}

function secureEquals(a, b) {
  if (a.length !== b.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

function istNow() {
  return Utilities.formatDate(new Date(), 'Asia/Kolkata', "yyyy-MM-dd'T'HH:mm:ss") + '+05:30';
}

function jsonOut(value) {
  return ContentService
    .createTextOutput(JSON.stringify(value))
    .setMimeType(ContentService.MimeType.JSON);
}
