/// Ticket-related data models — mirrors types/ticket.ts

class Ticket {
  final String id;
  final String numero;
  final String clientNom;
  final String? clientTel;
  final String? clientEmail;
  final String canal;
  final String statut;
  final int priorite;
  final String? description;
  final String? operateurId;
  final String? operateurName;
  final String? agenceId;
  final String? agenceName;
  final String? audioPath;
  final String? transcription;
  final String? transcriptionKeywords;
  final String? photosPaths;
  final bool consentementEnregistrement;
  final String createdAt;
  final String? updatedAt;
  final List<TicketAction> actions;

  Ticket({
    required this.id,
    required this.numero,
    required this.clientNom,
    this.clientTel,
    this.clientEmail,
    required this.canal,
    required this.statut,
    required this.priorite,
    this.description,
    this.operateurId,
    this.operateurName,
    this.agenceId,
    this.agenceName,
    this.audioPath,
    this.transcription,
    this.transcriptionKeywords,
    this.photosPaths,
    this.consentementEnregistrement = false,
    required this.createdAt,
    this.updatedAt,
    this.actions = const [],
  });

  Ticket copyWith({
    String? id,
    String? numero,
    String? clientNom,
    String? clientTel,
    String? clientEmail,
    String? canal,
    String? statut,
    int? priorite,
    String? description,
    String? operateurId,
    String? operateurName,
    String? agenceId,
    String? agenceName,
    String? audioPath,
    String? transcription,
    String? transcriptionKeywords,
    String? photosPaths,
    bool? consentementEnregistrement,
    String? createdAt,
    String? updatedAt,
    List<TicketAction>? actions,
  }) {
    return Ticket(
      id: id ?? this.id,
      numero: numero ?? this.numero,
      clientNom: clientNom ?? this.clientNom,
      clientTel: clientTel ?? this.clientTel,
      clientEmail: clientEmail ?? this.clientEmail,
      canal: canal ?? this.canal,
      statut: statut ?? this.statut,
      priorite: priorite ?? this.priorite,
      description: description ?? this.description,
      operateurId: operateurId ?? this.operateurId,
      operateurName: operateurName ?? this.operateurName,
      agenceId: agenceId ?? this.agenceId,
      agenceName: agenceName ?? this.agenceName,
      audioPath: audioPath ?? this.audioPath,
      transcription: transcription ?? this.transcription,
      transcriptionKeywords: transcriptionKeywords ?? this.transcriptionKeywords,
      photosPaths: photosPaths ?? this.photosPaths,
      consentementEnregistrement: consentementEnregistrement ?? this.consentementEnregistrement,
      createdAt: createdAt ?? this.createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      actions: actions ?? this.actions,
    );
  }

  factory Ticket.fromJson(Map<String, dynamic> json) {
    return Ticket(
      id: json['id'] ?? '',
      numero: json['numero'] ?? '',
      clientNom: json['clientNom'] ?? '',
      clientTel: json['clientTel'],
      clientEmail: json['clientEmail'],
      canal: json['canal'] ?? '',
      statut: json['statut'] ?? '',
      priorite: json['priorite'] ?? 3,
      description: json['description'],
      operateurId: json['operateurId'],
      operateurName: json['operateurName'],
      agenceId: json['agenceId'],
      agenceName: json['agenceName'],
      audioPath: json['audioPath'],
      transcription: json['transcription'],
      transcriptionKeywords: json['transcriptionKeywords'],
      photosPaths: json['photosPaths'],
      consentementEnregistrement: json['consentementEnregistrement'] ?? false,
      createdAt: json['createdAt'] ?? '',
      updatedAt: json['updatedAt'],
      actions: (json['actions'] as List?)?.map((a) => TicketAction.fromJson(a)).toList() ?? [],
    );
  }
}

class TicketAction {
  final String id;
  final String action;
  final String? oldValue;
  final String? newValue;
  final String? notes;
  final String? userName;
  final String createdAt;

  TicketAction({
    required this.id,
    required this.action,
    this.oldValue,
    this.newValue,
    this.notes,
    this.userName,
    required this.createdAt,
  });

  factory TicketAction.fromJson(Map<String, dynamic> json) {
    return TicketAction(
      id: json['id'] ?? '',
      action: json['action'] ?? '',
      oldValue: json['oldValue'],
      newValue: json['newValue'],
      notes: json['notes'],
      userName: json['userName'],
      createdAt: json['createdAt'] ?? '',
    );
  }
}

class TicketPage {
  final List<Ticket> content;
  final int totalPages;
  final int totalElements;

  TicketPage({required this.content, required this.totalPages, required this.totalElements});

  factory TicketPage.fromJson(Map<String, dynamic> json) {
    return TicketPage(
      content: (json['content'] as List?)?.map((t) => Ticket.fromJson(t)).toList() ?? [],
      totalPages: json['totalPages'] ?? 0,
      totalElements: json['totalElements'] ?? 0,
    );
  }
}

class QueueEntry {
  final String ticketId;
  final String ticketNumero;
  final String clientNom;
  final String canal;
  final int priorite;
  final int position;
  final String createdAt;

  QueueEntry({
    required this.ticketId,
    required this.ticketNumero,
    required this.clientNom,
    required this.canal,
    required this.priorite,
    required this.position,
    required this.createdAt,
  });

  factory QueueEntry.fromJson(Map<String, dynamic> json) {
    return QueueEntry(
      ticketId: json['ticketId'] ?? '',
      ticketNumero: json['ticketNumero'] ?? '',
      clientNom: json['clientNom'] ?? '',
      canal: json['canal'] ?? '',
      priorite: json['priorite'] ?? 3,
      position: json['position'] ?? 0,
      createdAt: json['createdAt'] ?? '',
    );
  }
}

class TicketStats {
  final int ticketsNouveau;
  final int ticketsEnCours;
  final int ticketsResolu;
  final int ticketsFerme;

  TicketStats({
    required this.ticketsNouveau,
    required this.ticketsEnCours,
    required this.ticketsResolu,
    required this.ticketsFerme,
  });

  factory TicketStats.fromJson(Map<String, dynamic> json) {
    return TicketStats(
      ticketsNouveau: json['nouveau'] ?? json['ticketsNouveau'] ?? 0,
      ticketsEnCours: json['enCours'] ?? json['ticketsEnCours'] ?? 0,
      ticketsResolu: json['resolu'] ?? json['ticketsResolu'] ?? 0,
      ticketsFerme: json['ferme'] ?? json['ticketsFerme'] ?? 0,
    );
  }
}

class TicketFilter {
  final int page;
  final int size;
  final String? canal;
  final String? statut;
  final int? priorite;
  final String? search;
  final String? sort;

  TicketFilter({this.page = 0, this.size = 15, this.canal, this.statut, this.priorite, this.search, this.sort});

  Map<String, dynamic> toQueryParams() {
    final params = <String, dynamic>{'page': page, 'size': size};
    if (canal != null) params['canal'] = canal;
    if (statut != null) params['statut'] = statut;
    if (priorite != null) params['priorite'] = priorite;
    if (search != null && search!.isNotEmpty) params['search'] = search;
    if (sort != null && sort!.isNotEmpty) params['sort'] = sort;
    return params;
  }
}
