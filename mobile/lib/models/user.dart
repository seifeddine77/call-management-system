/// User data models — mirrors types/user.ts

class User {
  final String id;
  final String email;
  final String nom;
  final String prenom;
  final String telephone;
  final String role;
  final String? agenceId;
  final String? agenceName;
  final bool actif;
  final String? planning;
  final String? createdAt;
  final String? lastLogin;

  User({
    required this.id,
    required this.email,
    required this.nom,
    required this.prenom,
    required this.telephone,
    required this.role,
    this.agenceId,
    this.agenceName,
    required this.actif,
    this.planning,
    this.createdAt,
    this.lastLogin,
  });

  String get fullName => '$prenom $nom';
  String get initials => '${prenom.isNotEmpty ? prenom[0] : ''}${nom.isNotEmpty ? nom[0] : ''}'.toUpperCase();

  User copyWith({
    String? id, String? email, String? nom, String? prenom,
    String? telephone, String? role, String? agenceId, String? agenceName,
    bool? actif, String? planning, String? createdAt, String? lastLogin,
  }) {
    return User(
      id: id ?? this.id,
      email: email ?? this.email,
      nom: nom ?? this.nom,
      prenom: prenom ?? this.prenom,
      telephone: telephone ?? this.telephone,
      role: role ?? this.role,
      agenceId: agenceId ?? this.agenceId,
      agenceName: agenceName ?? this.agenceName,
      actif: actif ?? this.actif,
      planning: planning ?? this.planning,
      createdAt: createdAt ?? this.createdAt,
      lastLogin: lastLogin ?? this.lastLogin,
    );
  }

  factory User.fromJson(Map<String, dynamic> json) {
    return User(
      id: json['id'] ?? '',
      email: json['email'] ?? '',
      nom: json['nom'] ?? '',
      prenom: json['prenom'] ?? '',
      telephone: json['telephone'] ?? '',
      role: json['role'] ?? '',
      agenceId: json['agenceId'],
      agenceName: json['agenceName'],
      actif: json['actif'] ?? true,
      planning: json['planning'],
      createdAt: json['createdAt'],
      lastLogin: json['lastLogin'],
    );
  }
}

class UserRequest {
  String nom;
  String prenom;
  String email;
  String password;
  String telephone;
  String role;
  String? agenceId;
  String? planning;

  UserRequest({
    this.nom = '',
    this.prenom = '',
    this.email = '',
    this.password = '',
    this.telephone = '',
    this.role = 'OPERATEUR',
    this.agenceId,
    this.planning,
  });

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'nom': nom,
      'prenom': prenom,
      'email': email,
      'password': password,
      'telephone': telephone,
      'role': role,
    };
    if (agenceId != null && agenceId!.isNotEmpty) map['agenceId'] = agenceId;
    if (planning != null && planning!.isNotEmpty) map['planning'] = planning;
    return map;
  }
}
