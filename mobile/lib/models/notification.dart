/// Notification data models — maps to backend NotificationLog entity

class NotificationLog {
  final String id;
  final String type;
  final String destinataire;
  final String sujet;
  final String statut;
  final int attempts;
  final String? errorMessage;
  final String createdAt;
  final String? sentAt;

  NotificationLog({
    required this.id,
    required this.type,
    required this.destinataire,
    required this.sujet,
    required this.statut,
    required this.attempts,
    this.errorMessage,
    required this.createdAt,
    this.sentAt,
  });

  factory NotificationLog.fromJson(Map<String, dynamic> json) {
    return NotificationLog(
      id: json['id'] ?? '',
      type: json['type'] ?? '',
      // Backend uses 'recipient', frontend TS uses 'destinataire'
      destinataire: json['recipient'] ?? json['destinataire'] ?? '',
      // Backend uses 'subject', frontend TS uses 'sujet'
      sujet: json['subject'] ?? json['sujet'] ?? '',
      // Backend uses 'status', frontend TS uses 'statut'
      statut: json['status'] ?? json['statut'] ?? '',
      attempts: json['attempts'] ?? 0,
      errorMessage: json['errorMessage'],
      createdAt: json['createdAt'] ?? '',
      sentAt: json['sentAt'],
    );
  }
}
