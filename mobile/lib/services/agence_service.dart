import '../config/api_config.dart';
import '../models/agence.dart';
import 'api_client.dart';

/// Agence service — mirrors services/agenceService.ts
class AgenceService {
  final _api = ApiClient();

  /// Get all agences (paginated)
  Future<AgencePageResponse> getAgences({int? page, int? size}) async {
    final params = <String, dynamic>{};
    if (page != null) params['page'] = page;
    if (size != null) params['size'] = size;

    final res = await _api.dio.get(ApiConfig.agencesPath, queryParameters: params);
    final data = res.data;
    if (data is List) {
      return AgencePageResponse(
        content: data.map<Agence>((a) => Agence.fromJson(a)).toList(),
        totalElements: data.length,
      );
    }
    final list = (data['content'] as List?) ?? [];
    return AgencePageResponse(
      content: list.map<Agence>((a) => Agence.fromJson(a)).toList(),
      totalElements: data['totalElements'] ?? list.length,
    );
  }

  /// Get a single agence by ID
  Future<Agence> getAgenceById(String id) async {
    final res = await _api.dio.get('${ApiConfig.agencesPath}/$id');
    return Agence.fromJson(res.data);
  }

  /// Create a new agence
  Future<Agence> createAgence(AgenceRequest request) async {
    final res = await _api.dio.post(ApiConfig.agencesPath, data: request.toJson());
    return Agence.fromJson(res.data);
  }

  /// Update an existing agence
  Future<Agence> updateAgence(String id, AgenceRequest request) async {
    final res = await _api.dio.put('${ApiConfig.agencesPath}/$id', data: request.toJson());
    return Agence.fromJson(res.data);
  }

  /// Delete (deactivate) an agence
  Future<void> deleteAgence(String id) async {
    await _api.dio.delete('${ApiConfig.agencesPath}/$id');
  }

  /// Assign a responsable (PATCH) — CDC 1.B
  Future<Agence> assignResponsable(String agenceId, String userId) async {
    final res = await _api.dio.patch(
      '${ApiConfig.agencesPath}/$agenceId/responsable',
      data: {'userId': userId},
    );
    return Agence.fromJson(res.data);
  }
}

/// Paginated agence response
class AgencePageResponse {
  final List<Agence> content;
  final int totalElements;

  AgencePageResponse({required this.content, required this.totalElements});
}

/// Request DTO for creating/updating an agence
class AgenceRequest {
  final String nom;
  final String adresse;
  final String telephone;
  final String email;
  final String? responsableId;
  final String? stockDescription;

  AgenceRequest({
    required this.nom,
    this.adresse = '',
    this.telephone = '',
    this.email = '',
    this.responsableId,
    this.stockDescription,
  });

  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'nom': nom,
      'adresse': adresse,
      'telephone': telephone,
      'email': email,
    };
    if (stockDescription != null && stockDescription!.isNotEmpty) {
      map['stockDescription'] = stockDescription;
    }
    return map;
  }
}
