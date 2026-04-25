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
  final String? audioPath;
  final String? transcription;
  final String? transcriptionKeywords;
  final String? photosPaths;
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
    this.audioPath,
    this.transcription,
    this.transcriptionKeywords,
    this.photosPaths,
    required this.createdAt,
    this.updatedAt,
    this.actions = const [],
  });

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
      audioPath: json['audioPath'],
      transcription: json['transcription'],
      transcriptionKeywords: json['transcriptionKeywords'],
      photosPaths: json['photosPaths'],
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
