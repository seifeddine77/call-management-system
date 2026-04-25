import '../config/api_config.dart';
import '../models/ticket.dart';
import 'api_client.dart';

/// Ticket service — mirrors services/ticketService.ts
class TicketService {
  final _api = ApiClient();

  Future<TicketPage> getTickets(TicketFilter filter) async {
    final res = await _api.dio.get(ApiConfig.ticketsPath, queryParameters: filter.toQueryParams());
    return TicketPage.fromJson(res.data);
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
}
