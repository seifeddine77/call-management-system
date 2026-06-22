import '../config/api_config.dart';
import '../models/user.dart';
import 'api_client.dart';

/// User service — mirrors services/userService.ts
/// Includes French ↔ English role mapping for backend compatibility.
class UserService {
  final _api = ApiClient();

  // ── Role mapping (frontend FR → backend EN) ──
  static const _roleToBackend = <String, String>{
    'OPERATEUR': 'OPERATOR',
    'SUPERVISEUR': 'SUPERVISOR',
    'ADMIN': 'ADMIN',
  };
  static const _roleToFrontend = <String, String>{
    'OPERATOR': 'OPERATEUR',
    'SUPERVISOR': 'SUPERVISEUR',
    'ADMIN': 'ADMIN',
  };

  static String toBackendRole(String role) => _roleToBackend[role] ?? role;
  static String toFrontendRole(String role) => _roleToFrontend[role] ?? role;

  /// Map a user response to use frontend role names
  User _mapUser(Map<String, dynamic> json) {
    final user = User.fromJson(json);
    return user.copyWith(role: toFrontendRole(user.role));
  }

  /// Get all users — optionally filtered by role
  Future<List<User>> getUsers({String? role, String? agenceId, int? page, int? size}) async {
    final params = <String, dynamic>{};
    if (role != null && role != 'ALL') params['role'] = toBackendRole(role);
    if (agenceId != null) params['agenceId'] = agenceId;
    if (page != null) params['page'] = page;
    if (size != null) params['size'] = size;

    final res = await _api.dio.get(ApiConfig.usersPath, queryParameters: params);
    final data = res.data;
    final list = data is List ? data : (data['content'] as List? ?? []);
    return list.map<User>((u) => _mapUser(u as Map<String, dynamic>)).toList();
  }

  /// Get a single user by ID
  Future<User> getUserById(String id) async {
    final res = await _api.dio.get('${ApiConfig.usersPath}/$id');
    return _mapUser(res.data);
  }

  /// Create a new user
  Future<User> createUser(UserRequest request) async {
    final payload = <String, dynamic>{
      'nom': request.nom,
      'prenom': request.prenom,
      'email': request.email,
      'role': toBackendRole(request.role),
    };
    if (request.password.isNotEmpty) payload['password'] = request.password;
    if (request.agenceId != null && request.agenceId!.isNotEmpty) {
      payload['agenceId'] = request.agenceId;
    }
    if (request.telephone.isNotEmpty) payload['telephone'] = request.telephone;
    if (request.planning != null && request.planning!.isNotEmpty) {
      payload['planning'] = request.planning;
    }

    final res = await _api.dio.post(ApiConfig.usersPath, data: payload);
    return _mapUser(res.data);
  }

  /// Update an existing user
  Future<User> updateUser(String id, UserRequest request) async {
    final payload = <String, dynamic>{
      'nom': request.nom,
      'prenom': request.prenom,
      'email': request.email,
      'role': toBackendRole(request.role),
    };
    if (request.password.isNotEmpty) payload['password'] = request.password;
    if (request.agenceId != null && request.agenceId!.isNotEmpty) {
      payload['agenceId'] = request.agenceId;
    } else {
      payload['agenceId'] = null;
    }
    if (request.telephone.isNotEmpty) payload['telephone'] = request.telephone;
    if (request.planning != null && request.planning!.isNotEmpty) {
      payload['planning'] = request.planning;
    }

    final res = await _api.dio.put('${ApiConfig.usersPath}/$id', data: payload);
    return _mapUser(res.data);
  }

  /// Delete (deactivate) a user
  Future<void> deleteUser(String id) async {
    await _api.dio.delete('${ApiConfig.usersPath}/$id');
  }

  /// Change password
  Future<void> changePassword(String id, {required String oldPassword, required String newPassword}) async {
    await _api.dio.patch('${ApiConfig.usersPath}/$id/password', data: {
      'oldPassword': oldPassword,
      'newPassword': newPassword,
    });
  }

  // ── Self-service (/me) : accessible à TOUS les rôles (profil + mot de passe propres) ──

  /// Récupère son propre profil (route /me non gatée par rôle).
  Future<User> getMyProfile() async {
    final res = await _api.dio.get('${ApiConfig.usersPath}/me');
    return _mapUser(res.data);
  }

  /// Met à jour son propre profil.
  Future<User> updateMyProfile(UserRequest request) async {
    final payload = <String, dynamic>{
      'nom': request.nom,
      'prenom': request.prenom,
      'email': request.email,
      'role': toBackendRole(request.role),
      'telephone': request.telephone,
    };
    if (request.agenceId != null && request.agenceId!.isNotEmpty) {
      payload['agenceId'] = request.agenceId;
    }
    final res = await _api.dio.put('${ApiConfig.usersPath}/me', data: payload);
    return _mapUser(res.data);
  }

  /// Change son propre mot de passe.
  Future<void> changeMyPassword({required String oldPassword, required String newPassword}) async {
    await _api.dio.patch('${ApiConfig.usersPath}/me/password', data: {
      'oldPassword': oldPassword,
      'newPassword': newPassword,
    });
  }
}
