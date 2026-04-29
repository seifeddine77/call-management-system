const { Client, LocalAuth } = require('whatsapp-web.js');
const qrcode = require('qrcode-terminal');
const amqp = require('amqplib');
const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');

// ── Remove Chromium lock file to prevent crash-loop on container restart ──
const SESSION_DIR = '/app/.wwebjs_auth/session';
['SingletonLock', 'SingletonSocket', 'SingletonCookiesLock'].forEach(f => {
    [SESSION_DIR, `${SESSION_DIR}/Default`].forEach(dir => {
        const p = path.join(dir, f);
        try { if (fs.existsSync(p)) { fs.unlinkSync(p); console.log(`🧹 Removed lock: ${p}`); } } catch(e) {}
        // Also remove symlinks
        try { fs.unlinkSync(p); } catch(e) {}
    });
});

// ╔══════════════════════════════════════════════════╗
// ║  CONFIGURATION                                   ║
// ╚══════════════════════════════════════════════════╝
const RABBITMQ_URL = process.env.RABBITMQ_URL || 'amqp://guest:guest@localhost:5672';
const ORGANISATION_SERVICE_URL = process.env.ORGANISATION_SERVICE_URL || 'http://organisation-service:8082';
const TICKET_SERVICE_URL = process.env.TICKET_SERVICE_URL || 'http://ticket-service:8083';
const DECISION_SERVICE_URL = process.env.DECISION_SERVICE_URL || 'http://decision-service:8085';
const AI_SERVICE_URL = process.env.AI_SERVICE_URL || 'http://ai-service:8000';

const EXCHANGE_NAME = 'user.exchange';
const QUEUE_NAME = 'whatsapp.agence.created';
const ROUTING_KEY = 'agence.created';
const MAX_RETRIES = 3;
const GROUP_PREFIX = 'AutoAssist';

// ── Amélioration #7 : Track last bot answer per group for feedback ──
const lastBotAnswers = new Map(); // groupId → { question, answer, messageId }

const HELP_MESSAGE = `🤖 *AutoAssist Bot — Commandes disponibles*\n\n` +
    `📊 */stats* — Statistiques des tickets\n` +
    `🔍 */ticket* T-xxx — Détail d'un ticket\n` +
    `📋 */queue* — File d'attente\n` +
    `⚖️ */decision* T-xxx — Décision IA d'un ticket\n` +
    `🏢 */agence* — Infos de l'agence\n` +
    `❓ */aide* — Cette aide\n\n` +
    `💬 Vous pouvez aussi poser des questions en langage naturel !\n` +
    `_Propulsé par RAG + Gemma 4 IA_`;

// ╔══════════════════════════════════════════════════╗
// ║  HTTP HELPERS                                    ║
// ╚══════════════════════════════════════════════════╝
function httpGet(baseUrl, path) {
    return new Promise((resolve, reject) => {
        const url = new URL(path, baseUrl);
        const client = url.protocol === 'https:' ? https : http;
        client.get(url.toString(), { timeout: 10000 }, (res) => {
            let data = '';
            res.on('data', chunk => data += chunk);
            res.on('end', () => {
                try { resolve(JSON.parse(data)); }
                catch { resolve(data); }
            });
        }).on('error', reject);
    });
}

function httpPost(baseUrl, path, body) {
    return new Promise((resolve, reject) => {
        const url = new URL(path, baseUrl);
        const data = JSON.stringify(body);
        const client = url.protocol === 'https:' ? https : http;
        const options = {
            hostname: url.hostname, port: url.port, path: url.pathname,
            method: 'POST', timeout: 90000, // 90s for RAG + Gemma
            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }
        };
        const req = client.request(options, (res) => {
            let resData = '';
            res.on('data', chunk => resData += chunk);
            res.on('end', () => {
                try { resolve(JSON.parse(resData)); }
                catch { resolve(resData); }
            });
        });
        req.on('error', reject);
        req.write(data);
        req.end();
    });
}

function httpPatch(baseUrl, path, body) {
    return new Promise((resolve, reject) => {
        const url = new URL(path, baseUrl);
        const data = JSON.stringify(body);
        const client = url.protocol === 'https:' ? https : http;
        const options = {
            hostname: url.hostname, port: url.port, path: url.pathname,
            method: 'PATCH', timeout: 10000,
            headers: { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) }
        };
        const req = client.request(options, (res) => {
            let resData = '';
            res.on('data', chunk => resData += chunk);
            res.on('end', () => resolve(resData));
        });
        req.on('error', reject);
        req.write(data);
        req.end();
    });
}

// ╔══════════════════════════════════════════════════╗
// ║  WHATSAPP CLIENT                                 ║
// ╚══════════════════════════════════════════════════╝
let whatsappReady = false;

const client = new Client({
    authStrategy: new LocalAuth(),
    puppeteer: { headless: true, args: ['--no-sandbox', '--disable-setuid-sandbox'] }
});

client.on('qr', (qr) => {
    console.log('\n╔══════════════════════════════════════════════════════╗');
    console.log('║  📱 SCANNEZ CE QR CODE AVEC VOTRE WHATSAPP          ║');
    console.log('╚══════════════════════════════════════════════════════╝\n');
    qrcode.generate(qr, { small: true });
});

client.on('ready', () => { whatsappReady = true; console.log('✅ WhatsApp connecté !'); });
client.on('authenticated', () => console.log('🔐 Session authentifiée.'));
client.on('auth_failure', (msg) => console.error('❌ Auth échouée:', msg));
client.on('disconnected', (reason) => {
    whatsappReady = false;
    console.error('⚠️ Déconnecté:', reason);
    client.initialize();
});

// ╔══════════════════════════════════════════════════╗
// ║  COMMANDES RAPIDES (sans IA)                     ║
// ╚══════════════════════════════════════════════════╝

async function handleStats(chat) {
    try {
        const data = await httpGet(TICKET_SERVICE_URL, '/api/tickets/stats');
        await chat.sendMessage(
            `📊 *Statistiques des tickets*\n\n` +
            `🆕 Nouveau : *${data.nouveau || 0}*\n` +
            `⏳ En cours : *${data.enCours || 0}*\n` +
            `✅ Résolu : *${data.resolu || 0}*\n` +
            `🔒 Fermé : *${data.ferme || 0}*\n` +
            `━━━━━━━━━━━━━━━━━━━━\n` +
            `📈 Total : *${data.total || 0}*`
        );
    } catch (err) { await chat.sendMessage(`❌ Erreur stats: ${err.message}`); }
}

async function handleTicket(chat, ref) {
    try {
        const data = await httpGet(TICKET_SERVICE_URL, '/api/tickets?page=0&size=200');
        const tickets = data.content || [];
        const t = tickets.find(t => t.numero === ref || t.id === ref);
        if (!t) { await chat.sendMessage(`🔍 Ticket *${ref}* non trouvé.`); return; }

        const se = { 'NOUVEAU': '🆕', 'EN_COURS': '⏳', 'RESOLU': '✅', 'FERME': '🔒' };
        const ce = { 'CALL': '📞', 'EMAIL': '📧', 'WEB': '🌐' };
        await chat.sendMessage(
            `🎫 *Ticket ${t.numero}*\n\n` +
            `${se[t.statut] || '❓'} Statut : *${t.statut}*\n` +
            `${ce[t.canal] || '📡'} Canal : *${t.canal}*\n` +
            `👤 Client : *${t.clientNom || 'N/A'}*\n` +
            `📱 Tél : ${t.clientTel || 'N/A'}\n` +
            `⭐ Priorité : ${t.priorite || 'N/A'}\n` +
            `📝 ${t.description ? t.description.substring(0, 200) : 'N/A'}\n` +
            (t.transcription ? `\n🎙️ _${t.transcription.substring(0, 150)}..._` : '') +
            `\n📅 Créé : ${t.createdAt ? new Date(t.createdAt).toLocaleString('fr-TN') : 'N/A'}`
        );
    } catch (err) { await chat.sendMessage(`❌ Erreur: ${err.message}`); }
}

async function handleQueue(chat, groupName) {
    try {
        // ── Resolve agence from group name ──
        const agenceName = groupName.replace(`${GROUP_PREFIX} — `, '').trim();
        let agenceId = null;

        try {
            const orgData = await httpGet(ORGANISATION_SERVICE_URL, '/api/agences?page=0&size=100');
            const agence = (orgData.content || []).find(a => a.nom === agenceName);
            if (agence) agenceId = agence.id;
        } catch (e) { console.log('⚠️ Could not resolve agence:', e.message); }

        // ── Fetch queue (filtered by agence if found) ──
        const queuePath = agenceId
            ? `/api/tickets/queue?agenceId=${agenceId}`
            : '/api/tickets/queue';
        const data = await httpGet(TICKET_SERVICE_URL, queuePath);
        const tickets = data.tickets || [];

        if (tickets.length === 0) {
            await chat.sendMessage(`✅ *File d'attente vide !* Aucun ticket en attente${agenceId ? ` pour *${agenceName}*` : ''}.`);
            return;
        }

        let msg = `📋 *File d'attente${agenceId ? ' — ' + agenceName : ''}* — ${tickets.length} ticket(s)\n\n`;
        tickets.forEach((t, i) => {
            msg += `${i + 1}. *${t.ticketNumero || t.numero || 'N/A'}* — ${t.clientNom || 'Client'} (${t.canal})\n`;
        });
        await chat.sendMessage(msg);
    } catch (err) { await chat.sendMessage(`❌ Erreur: ${err.message}`); }
}

async function handleDecision(chat, ref) {
    try {
        const ticketsData = await httpGet(TICKET_SERVICE_URL, '/api/tickets');
        const t = (ticketsData.content || []).find(t => t.numero === ref || t.id === ref);
        if (!t) { await chat.sendMessage(`🔍 Ticket *${ref}* non trouvé.`); return; }

        const dec = await httpGet(DECISION_SERVICE_URL, `/api/decisions/ticket/${t.id}`);
        if (!dec || !dec.decision) {
            await chat.sendMessage(`⚖️ Aucune décision pour *${ref}*. Lancez l'analyse depuis la plateforme.`);
            return;
        }
        const de = { 'FAVORABLE': '✅', 'DEFAVORABLE': '❌', 'ETUDE': '🔍' };
        await chat.sendMessage(
            `⚖️ *Décision — ${t.numero}*\n\n` +
            `${de[dec.decision] || '❓'} Décision : *${dec.decision}*\n` +
            `📊 Score : *${dec.score}/100*\n` +
            `💡 Source : *${dec.source}*\n` +
            `📝 _${dec.motif || 'N/A'}_` +
            (dec.finalDecision ? `\n\n✍️ Décision finale : *${dec.finalDecision}*` : '')
        );
    } catch (err) { await chat.sendMessage(`❌ Erreur: ${err.message}`); }
}

async function handleAgence(chat, groupName) {
    try {
        const name = groupName.replace(`${GROUP_PREFIX} — `, '').trim();
        const data = await httpGet(ORGANISATION_SERVICE_URL, '/api/agences?page=0&size=100');
        const a = (data.content || []).find(a => a.nom === name);
        if (!a) { await chat.sendMessage(`🏢 Infos indisponibles pour *${name}*`); return; }

        await chat.sendMessage(
            `🏢 *${a.nom}*\n\n` +
            `📍 ${a.adresse || 'Non renseignée'}\n` +
            `📱 ${a.telephone || 'N/A'}\n` +
            `📧 ${a.email || 'N/A'}\n` +
            `👤 Responsable : ${a.responsableName || 'Non assigné'}\n` +
            `✅ ${a.actif ? 'Active' : 'Inactive'}`
        );
    } catch (err) { await chat.sendMessage(`❌ Erreur: ${err.message}`); }
}

// ╔══════════════════════════════════════════════════╗
// ║  CHATBOT RAG — Langage naturel via ai-service    ║
// ╚══════════════════════════════════════════════════╝

async function handleRAGChat(chat, question, groupName) {
    try {
        // Show typing indicator while waiting for RAG + Gemma
        await chat.sendStateTyping();

        console.log(`🧠 RAG query: "${question.substring(0, 80)}..."`);

        const result = await httpPost(AI_SERVICE_URL, '/chat/', {
            question: question,
            group_name: groupName,
        });

        if (result.answer) {
            let response = result.answer;

            // Add sources footer if available
            if (result.sources && result.sources.length > 0) {
                response += `\n\n📚 _Sources : ${result.sources.slice(0, 3).join(', ')}_`;
            }

            // Add response time
            if (result.response_time_ms) {
                response += `\n⏱️ _${(result.response_time_ms / 1000).toFixed(1)}s_`;
            }

            // Add feedback prompt
            response += `\n\n_Réagissez 👍 ou 👎 pour évaluer cette réponse_`;

            const sentMsg = await chat.sendMessage(response);

            // ── Amélioration #7 : Track answer for feedback ──
            lastBotAnswers.set(chat.id._serialized, {
                question: question,
                answer: result.answer,
                messageId: sentMsg.id._serialized,
                responseId: result.response_id,
            });

            console.log(`✅ RAG response sent (${result.documents_retrieved} docs, ${result.response_time_ms}ms)`);
        } else {
            await chat.sendMessage(
                `⚠️ Je n'ai pas pu répondre. Essayez une commande directe :\n${HELP_MESSAGE}`
            );
        }
    } catch (err) {
        console.error('❌ RAG error:', err.message);
        await chat.sendMessage(
            `⚠️ L'IA est temporairement indisponible.\n` +
            `Utilisez les commandes directes : */stats*, */ticket*, */aide*`
        );
    }
}

// ╔══════════════════════════════════════════════════╗
// ║  VOICE MESSAGE HANDLER — Whisper + RAG          ║
// ╚══════════════════════════════════════════════════╝

async function handleVoiceMessage(chat, msg) {
    try {
        await chat.sendStateRecording(); // Show "recording..." indicator

        console.log(`🎙️ Voice message received in [${chat.name}]`);

        // Download the audio from WhatsApp
        const media = await msg.downloadMedia();
        if (!media || !media.data) {
            await chat.sendMessage(`⚠️ Impossible de télécharger le message vocal.`);
            return;
        }

        // Convert base64 to buffer and send to ai-service /transcribe/quick
        const audioBuffer = Buffer.from(media.data, 'base64');
        const FormData = (await import('form-data')).default;
        const form = new FormData();
        form.append('file', audioBuffer, {
            filename: 'voice.ogg',
            contentType: media.mimetype || 'audio/ogg',
        });

        console.log(`🎙️ Sending ${audioBuffer.length} bytes to Whisper...`);

        const transcribeResponse = await new Promise((resolve, reject) => {
            const http = require('http');
            const req = http.request({
                hostname: 'ai-service',
                port: 8000,
                path: '/transcribe/quick',
                method: 'POST',
                headers: form.getHeaders(),
                timeout: 120000, // 2 min for Whisper on CPU
            }, (res) => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => {
                    try { resolve(JSON.parse(data)); }
                    catch (e) { reject(new Error(`Parse error: ${data.substring(0, 200)}`)); }
                });
            });
            req.on('error', reject);
            req.on('timeout', () => { req.destroy(); reject(new Error('Whisper timeout')); });
            form.pipe(req);
        });

        const transcribedText = transcribeResponse.text;
        const duration = transcribeResponse.duration_seconds || 0;

        if (!transcribedText || transcribedText.trim().length < 2) {
            await chat.sendMessage(
                `🎙️ J'ai écouté votre message vocal (${duration.toFixed(1)}s) mais je n'ai pas compris le contenu.\n` +
                `Essayez de parler plus clairement ou envoyez un message texte.`
            );
            return;
        }

        console.log(`🎙️ Transcribed (${duration.toFixed(1)}s): "${transcribedText.substring(0, 100)}..."`);

        // Show what was understood
        await chat.sendMessage(
            `🎙️ _Message vocal (${duration.toFixed(1)}s) :_\n` +
            `"${transcribedText.length > 200 ? transcribedText.substring(0, 200) + '...' : transcribedText}"\n\n` +
            `⏳ _Analyse en cours..._`
        );

        // Check if it mentions a ticket
        const ticketMatch = transcribedText.match(/T-\d{8}-\d+/i);
        if (ticketMatch) {
            await chat.sendStateTyping();
            return await handleTicket(chat, ticketMatch[0]);
        }

        // Send through RAG
        await handleRAGChat(chat, transcribedText, chat.name);

    } catch (err) {
        console.error('❌ Voice message error:', err.message);
        await chat.sendMessage(
            `⚠️ Erreur lors du traitement du message vocal.\n` +
            `_${err.message}_\n\n` +
            `Envoyez votre question en texte à la place.`
        );
    }
}

// ╔══════════════════════════════════════════════════╗
// ║  MESSAGE LISTENER                                ║
// ╚══════════════════════════════════════════════════╝

// Track bot-sent message IDs to avoid infinite loops
const botSentMessageIds = new Set();

// Helper: strip WhatsApp formatting characters (* _ ~ `) 
// WhatsApp sends */stats* when user taps bold text
function stripFormatting(text) {
    return text.replace(/^\*+|\*+$/g, '')   // leading/trailing *
               .replace(/^_+|_+$/g, '')     // leading/trailing _
               .replace(/^~+|~+$/g, '')     // leading/trailing ~
               .replace(/^`+|`+$/g, '')     // leading/trailing `
               .trim();
}

client.on('message_create', async (msg) => {
    try {

        const chat = await msg.getChat();
        if (!chat.isGroup || !chat.name?.startsWith(GROUP_PREFIX)) return;

        // ── VOICE MESSAGE DETECTION ──
        // Handle voice notes (ptt) and audio messages → Whisper + RAG
        // Note: fromMe is allowed here because the bot NEVER sends audio messages
        if ((msg.type === 'ptt' || msg.type === 'audio') && msg.hasMedia) {
            return await handleVoiceMessage(chat, msg);
        }

        // Strip WhatsApp formatting from the body
        const rawBody = msg.body.trim();
        if (!rawBody) return;
        const body = stripFormatting(rawBody);
        if (!body) return;

        // ── ANTI-LOOP: Skip bot's own responses (not admin messages) ──
        // Bot responses always contain specific emoji patterns at the start
        if (msg.fromMe) {
            const botPatterns = ['📊', '🔍', '📋', '🏢', '⚖️', '🤖', '⚠️', '✅', '🎫', '❌', '📚', '⏱️', '🎙️', '⏳', '👋', '_Réagissez'];
            const startsWithBot = botPatterns.some(p => msg.body.includes(p));
            if (startsWithBot) {
                console.log(`🔇 Anti-loop: skipped own bot message`);
                return;
            }
            // Also skip very long fromMe messages (bot responses are verbose)
            if (msg.body.length > 150) {
                console.log(`🔇 Anti-loop: skipped long fromMe (${msg.body.length} chars)`);
                return;
            }
            console.log(`📱 Processing fromMe message: "${body.substring(0, 60)}"`);
        }

        console.log(`💬 [${chat.name}] ${body.substring(0, 100)} (fromMe=${msg.fromMe})`);

        // ── Slash commands (with typing indicator) ──
        const lower = body.toLowerCase();
        
        try {
            if (lower === '/stats' || lower === '/stat') {
                await chat.sendStateTyping();
                return await handleStats(chat);
            }
            if (lower.startsWith('/ticket ')) {
                await chat.sendStateTyping();
                return await handleTicket(chat, body.substring(8).trim());
            }
            if (lower === '/queue' || lower === '/file') {
                await chat.sendStateTyping();
                return await handleQueue(chat, chat.name);
            }
            if (lower.startsWith('/decision ')) {
                await chat.sendStateTyping();
                return await handleDecision(chat, body.substring(10).trim());
            }
            if (lower === '/agence') {
                await chat.sendStateTyping();
                return await handleAgence(chat, chat.name);
            }
            if (lower === '/aide' || lower === '/help') return await chat.sendMessage(HELP_MESSAGE);
        } catch (cmdErr) {
            console.error(`❌ Command error (${lower}):`, cmdErr.message);
            await chat.sendMessage(`⚠️ Erreur lors de l'exécution de la commande. Réessayez.`);
            return;
        }

        // ── Smart greeting handler (instant, no RAG needed) ──
        const greetings = ['bonjour', 'bonsoir', 'salut', 'hello', 'hi', 'hey', 'salam', 'مرحبا', 'اهلا', 'yo'];
        const cleanLower = lower.replace(/[!?.,:;]+/g, '').trim();
        if (greetings.includes(cleanLower) || cleanLower.startsWith('bonjour ') || cleanLower.startsWith('salut ')) {
            const hour = new Date().getHours();
            const greeting = hour < 12 ? 'Bonjour' : hour < 18 ? 'Bon après-midi' : 'Bonsoir';
            console.log(`👋 Sending greeting to [${chat.name}]...`);
            try {
                await chat.sendMessage(
                    `👋 *${greeting} !*\n\n` +
                    `Je suis *AutoAssist*, votre assistant IA pour la gestion des réclamations pièces auto. 🚗\n\n` +
                    `💡 Je peux vous aider avec :\n` +
                    `• 📊 Les statistiques → tapez */stats*\n` +
                    `• 🎫 Un ticket → tapez */ticket T-xxx*\n` +
                    `• 📋 La file d'attente → tapez */queue*\n` +
                    `• ❓ Toute question → posez-la en langage naturel !\n\n` +
                    `_Propulsé par RAG + Gemma 4 IA_ 🧠`
                );
                console.log(`✅ Greeting sent to [${chat.name}]`);
            } catch (greetErr) {
                console.error(`❌ Greeting send failed:`, greetErr.message);
            }
            return;
        }

        // ── Natural language ticket extraction ──
        // "statut de ticket T-xxx" or "ticket T-xxx" → auto /ticket
        const ticketMatch = body.match(/T-\d{8}-\d+/i);
        if (ticketMatch) {
            await chat.sendStateTyping();
            console.log(`🎫 Auto-detected ticket: ${ticketMatch[0]}`);
            return await handleTicket(chat, ticketMatch[0]);
        }

        // ── Natural language → RAG + Gemma 4 ──
        // Send ALL non-command text messages to RAG (the AI decides relevance)
        // Only skip very short messages (< 3 chars) that are likely typos
        if (body.length >= 3) {
            return await handleRAGChat(chat, body, chat.name);
        }

        // Messages too short → ignore silently
    } catch (error) {
        console.error('❌ Message error:', error.message);
    }
});

// ╔══════════════════════════════════════════════════╗
// ║  AMÉLIORATION #7 : FEEDBACK (Réactions WhatsApp) ║
// ╚══════════════════════════════════════════════════╝

client.on('message_reaction', async (reaction) => {
    try {
        const emoji = reaction.reaction;
        if (!emoji) return;

        // Only process 👍 and 👎
        const isPositive = ['👍', '👍🏻', '👍🏼', '👍🏽', '👍🏾', '👍🏿'].includes(emoji);
        const isNegative = ['👎', '👎🏻', '👎🏼', '👎🏽', '👎🏾', '👎🏿'].includes(emoji);
        if (!isPositive && !isNegative) return;

        // Find the last bot answer for this chat
        const chatId = reaction.msgId?.remote;
        if (!chatId) return;

        const lastAnswer = lastBotAnswers.get(chatId);
        if (!lastAnswer) return;

        const rating = isPositive ? 'positive' : 'negative';

        // Send feedback to ai-service
        try {
            await httpPost(AI_SERVICE_URL, '/chat/feedback', {
                question: lastAnswer.question,
                answer: lastAnswer.answer,
                rating: rating,
                group_name: chatId,
            });
            console.log(`${isPositive ? '👍' : '👎'} Feedback enregistré: ${rating}`);
        } catch (e) {
            console.warn(`⚠️ Feedback non enregistré: ${e.message}`);
        }
    } catch (err) {
        console.error('❌ Reaction error:', err.message);
    }
});

// ╔══════════════════════════════════════════════════╗
// ║  CRÉATION GROUPE WHATSAPP (RabbitMQ)             ║
// ╚══════════════════════════════════════════════════╝

async function createWhatsAppGroup(agenceData, retryCount = 0) {
    if (!whatsappReady) {
        if (retryCount < MAX_RETRIES) setTimeout(() => createWhatsAppGroup(agenceData, retryCount + 1), 5000);
        return;
    }

    const groupName = `${GROUP_PREFIX} — ${agenceData.nom}`;
    console.log('═══════════════════════════════════════════════');
    console.log(`📢 Agence "${agenceData.nom}" créée !`);
    console.log(`📱 Tél : ${agenceData.telephone}`);
    console.log('═══════════════════════════════════════════════');

    try {
        const cleanNumber = agenceData.telephone.replace(/[^0-9]/g, '');
        const result = await client.createGroup(groupName, [`${cleanNumber}@c.us`]);
        const groupId = result.gid._serialized;

        console.log(`✅ Groupe "${groupName}" créé (${groupId})`);

        const groupChat = await client.getChatById(groupId);
        await groupChat.sendMessage(
            `🏢 *Bienvenue — ${agenceData.nom}*\n\n` +
            `📍 ${agenceData.adresse || ''}\n📱 ${agenceData.telephone}\n📧 ${agenceData.email || ''}\n\n` +
            `🤖 *Chatbot IA disponible !*\n` +
            `Tapez */aide* pour les commandes.\n` +
            `Posez vos questions en langage naturel !\n\n` +
            `_Propulsé par RAG + Gemma 4_`
        );

        try {
            await httpPatch(ORGANISATION_SERVICE_URL, `/api/agences/${agenceData.agenceId}/whatsapp-group`, { groupId });
            console.log(`💾 Group ID sauvegardé pour ${agenceData.agenceId}`);
        } catch (e) { console.warn(`⚠️ Callback échoué: ${e.message}`); }

        console.log('🎉 En attente...\n');
    } catch (error) {
        console.error(`❌ Erreur: ${error.message}`);
        if (retryCount < MAX_RETRIES) {
            const delay = Math.pow(2, retryCount) * 2000;
            setTimeout(() => createWhatsAppGroup(agenceData, retryCount + 1), delay);
        }
    }
}

// ╔══════════════════════════════════════════════════╗
// ║  RABBITMQ LISTENER                               ║
// ╚══════════════════════════════════════════════════╝

async function startRabbitMQListener() {
    try {
        console.log('🐰 Connexion RabbitMQ...');
        const connection = await amqp.connect(RABBITMQ_URL);
        const channel = await connection.createChannel();

        await channel.assertExchange(EXCHANGE_NAME, 'topic', { durable: true });
        await channel.assertQueue(QUEUE_NAME, { durable: true });
        await channel.bindQueue(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);
        console.log(`✅ RabbitMQ connecté ! Écoute "${ROUTING_KEY}"...`);

        channel.consume(QUEUE_NAME, async (msg) => {
            if (msg) {
                try {
                    const agenceData = JSON.parse(msg.content.toString());
                    console.log(`\n📩 Agence reçue: ${JSON.stringify(agenceData)}`);
                    await createWhatsAppGroup(agenceData);
                    channel.ack(msg);
                } catch (e) { console.error('❌ Parse error:', e.message); channel.nack(msg, false, false); }
            }
        });

        connection.on('error', (e) => console.error('❌ RabbitMQ:', e.message));
        connection.on('close', () => { console.log('⚠️ RabbitMQ fermé, reconnexion...'); setTimeout(startRabbitMQListener, 10000); });

        // ── Amélioration #8 : Auto-refresh quand les règles changent ──
        try {
            const RULES_QUEUE = 'whatsapp.rules.refresh';
            await channel.assertQueue(RULES_QUEUE, { durable: true });
            await channel.bindQueue(RULES_QUEUE, EXCHANGE_NAME, 'rule.created');
            await channel.bindQueue(RULES_QUEUE, EXCHANGE_NAME, 'rule.updated');
            await channel.bindQueue(RULES_QUEUE, EXCHANGE_NAME, 'rule.deleted');
            console.log('🔄 Écoute des changements de règles pour auto-refresh RAG');

            channel.consume(RULES_QUEUE, async (msg) => {
                if (msg) {
                    console.log('🔄 Règle modifiée → Refresh du RAG...');
                    try {
                        await httpPost(AI_SERVICE_URL, '/chat/refresh', {});
                        console.log('✅ RAG rafraîchi automatiquement !');
                    } catch (e) {
                        console.warn(`⚠️ Auto-refresh échoué: ${e.message}`);
                    }
                    channel.ack(msg);
                }
            });
        } catch (e) {
            console.warn('⚠️ Auto-refresh des règles non configuré:', e.message);
        }
    } catch (e) { console.error('❌ RabbitMQ:', e.message); setTimeout(startRabbitMQListener, 10000); }
}

// ╔══════════════════════════════════════════════════╗
// ║  DÉMARRAGE                                       ║
// ╚══════════════════════════════════════════════════╝

console.log('\n🚀 ════════════════════════════════════════════');
console.log('   AutoAssist — WhatsApp Service + Chatbot IA');
console.log('   📡 Commandes rapides : /stats, /ticket...');
console.log('   🧠 RAG + Gemma 4 pour le langage naturel');
console.log('   📚 Base de connaissances vectorielle ChromaDB');
console.log('   🌍 Embeddings multilingues (FR/AR/EN)');
console.log('   💬 Mémoire de conversation par groupe');
console.log('   👍 Feedback loop (réactions WhatsApp)');
console.log('   🔄 Auto-refresh des règles métier');
console.log('════════════════════════════════════════════\n');

client.initialize();
startRabbitMQListener();
