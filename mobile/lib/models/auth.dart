/// Auth-related data models

class LoginRequest {
  final String email;
  final String password;

  LoginRequest({required this.email, required this.password});

  Map<String, dynamic> toJson() => {'email': email, 'password': password};
}

class AuthUser {
  final String userId;
  final String email;
  final String nom;
  final String prenom;
  final String role;
  final String? agenceId;
  final String accessToken;
  final String refreshToken;

  AuthUser({
    required this.userId,
    required this.email,
    required this.nom,
    required this.prenom,
    required this.role,
    this.agenceId,
    required this.accessToken,
    required this.refreshToken,
  });

  factory AuthUser.fromJson(Map<String, dynamic> json) {
    // Backend wraps user info in a 'user' sub-object
    final userObj = json['user'] as Map<String, dynamic>? ?? json;
    return AuthUser(
      userId: userObj['id']?.toString() ?? userObj['userId']?.toString() ?? '',
      email: userObj['email'] ?? json['email'] ?? '',
      nom: userObj['nom'] ?? json['nom'] ?? '',
      prenom: userObj['prenom'] ?? json['prenom'] ?? '',
      role: userObj['role'] ?? json['role'] ?? '',
      agenceId: userObj['agenceId']?.toString() ?? json['agenceId']?.toString(),
      accessToken: json['accessToken'] ?? json['token'] ?? '',
      refreshToken: json['refreshToken'] ?? '',
    );
  }

  Map<String, dynamic> toJson() => {
    'userId': userId,
    'email': email,
    'nom': nom,
    'prenom': prenom,
    'role': role,
    'agenceId': agenceId,
    'accessToken': accessToken,
    'refreshToken': refreshToken,
  };

  bool get isAdmin => role == 'ADMIN';
  bool get isSupervisor => role == 'SUPERVISOR' || role == 'SUPERVISEUR' || role == 'ADMIN';
  bool get isOperator => role == 'OPERATEUR' || role == 'OPERATOR';
}
