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
    final prefs = await SharedPreferences.getInstance();

    try {
      final res = await _api.dio.get(ApiConfig.ticketsPath, queryParameters: filter.toQueryParams());
      final data = res.data;
      
      // Save to cache for the first page only to prevent huge cache files
      if (filter.page == 0 && filter.search == null) {
        await prefs.setString(_cacheKey, jsonEncode(data));
      }
      
      return TicketPage.fromJson(data);
    } catch (e) {
      // If network fails, try to load from cache
      if (filter.page == 0 && filter.search == null) {
        final cachedStr = prefs.getString(_cacheKey);
        if (cachedStr != null) {
          final data = jsonDecode(cachedStr);
          return TicketPage.fromJson(data);
        }
      }
      rethrow;
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
