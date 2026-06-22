import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:dio/dio.dart';
import '../config/api_config.dart';
import '../models/ticket.dart';
import 'api_client.dart';

/// Ticket service — mirrors services/ticketService.ts
class TicketService {
  final _api = ApiClient();
  static const _cacheKey = 'tickets_cache';

  Future<TicketPage> getTickets(TicketFilter filter) async {
    // Le cache local n'est qu'un confort offline : il ne doit JAMAIS bloquer le
    // chargement. Sur Flutter Web, shared_preferences peut lever MissingPluginException ;
    // on l'isole donc dans des helpers best-effort.
    final bool cacheable = filter.page == 0 && filter.search == null;
    try {
      final res = await _api.dio.get(ApiConfig.ticketsPath, queryParameters: filter.toQueryParams());
      final data = res.data;
      if (cacheable) {
        await _writeCache(data); // best-effort, n'échoue jamais
      }
      return TicketPage.fromJson(data);
    } catch (e) {
      // Réseau KO → tenter le cache local (best-effort)
      if (cacheable) {
        final cached = await _readCache();
        if (cached != null) {
          return TicketPage.fromJson(cached);
        }
      }
      rethrow;
    }
  }

  /// Écrit le cache des tickets sans jamais propager d'erreur (ex. Web sans shared_preferences).
  Future<void> _writeCache(dynamic data) async {
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_cacheKey, jsonEncode(data));
    } catch (_) {
      // Stockage local indisponible → on ignore silencieusement le cache.
    }
  }

  /// Lit le cache des tickets sans jamais propager d'erreur ; retourne null si indisponible.
  Future<dynamic> _readCache() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final cachedStr = prefs.getString(_cacheKey);
      return cachedStr != null ? jsonDecode(cachedStr) : null;
    } catch (_) {
      return null;
    }
  }

  Future<Ticket> getTicketById(String id) async {
    final res = await _api.dio.get('${ApiConfig.ticketsPath}/$id');
    return Ticket.fromJson(res.data);
  }

  Future<Map<String, dynamic>> getQueue() async {
    final res = await _api.dio.get(ApiConfig.queuePath);
    return res.data;
  }

  Future<TicketStats> getStats() async {
    final res = await _api.dio.get(ApiConfig.ticketStatsPath);
    return TicketStats.fromJson(res.data);
  }

  Future<void> assignTicket(String ticketId, String operateurId) async {
    await _api.dio.patch('${ApiConfig.ticketsPath}/$ticketId/assign', data: {
      'operateurId': operateurId,
    });
  }

  Future<Ticket> updateStatut(String ticketId, String statut, {String? notes}) async {
    final res = await _api.dio.patch('${ApiConfig.ticketsPath}/$ticketId/statut', data: {
      'newStatut': statut,
      if (notes != null) 'notes': notes,
    });
    return Ticket.fromJson(res.data);
  }

  /// RGPD — Anonymise un ticket (droit à l'oubli, ADMIN uniquement).
  /// Déclenche l'effacement durable des données personnelles sur tous les services.
  Future<Ticket> anonymize(String ticketId) async {
    final res = await _api.dio.post('${ApiConfig.ticketsPath}/$ticketId/anonymize');
    return Ticket.fromJson(res.data);
  }

  Future<Ticket> uploadPhotos(String ticketId, List<dynamic> files) async {
    final formData = FormData();
    for (final file in files) {
      final path = file.path as String;
      final multipartFile = await MultipartFile.fromFile(
        path,
        filename: path.split('/').last,
      );
      formData.files.add(MapEntry('files', multipartFile));
    }
    final res = await _api.dio.post(
      '${ApiConfig.ticketsPath}/$ticketId/photos',
      data: formData,
      options: Options(contentType: 'multipart/form-data'),
    );
    return Ticket.fromJson(res.data);
  }
}
