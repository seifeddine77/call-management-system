/// Agence data models — mirrors types/agence.ts

class Agence {
  final String id;
  final String nom;
  final String adresse;
  final String telephone;
  final String email;
  final String? responsableId;
  final String? responsableName;
  final String? stockDescription;
  final bool actif;

  Agence({
    required this.id,
    required this.nom,
    this.adresse = '',
    this.telephone = '',
    this.email = '',
    this.responsableId,
    this.responsableName,
    this.stockDescription,
    this.actif = true,
  });

  factory Agence.fromJson(Map<String, dynamic> json) {
    return Agence(
      id: json['id'] ?? '',
      nom: json['nom'] ?? '',
      adresse: json['adresse'] ?? '',
      telephone: json['telephone'] ?? '',
      email: json['email'] ?? '',
      responsableId: json['responsableId'],
      responsableName: json['responsableName'],
      stockDescription: json['stockDescription'],
      actif: json['actif'] ?? true,
    );
  }
}

class AgencePage {
  final List<Agence> content;

  AgencePage({required this.content});

  factory AgencePage.fromJson(Map<String, dynamic> json) {
    final list = json['content'] as List? ?? (json is List ? json : []);
    return AgencePage(
      content: (list is List) ? list.map((a) => Agence.fromJson(a)).toList() : [],
    );
  }
}
