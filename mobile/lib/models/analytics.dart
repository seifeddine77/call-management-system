/// Analytics data models — mirrors types/analytics.ts

class KpiResponse {
  final int totalTickets;
  final int ticketsNouveau;
  final double tauxResolution;
  final double tempsMoyenMinutes;
  final int ticketsCall;
  final int ticketsEmail;
  final int ticketsWeb;
  final int ticketsEnCours;
  final int ticketsResolu;
  final int ticketsFerme;
  final int decisionsAiFavorable;
  final int decisionsAiDefavorable;
  final int decisionsAiEtude;

  KpiResponse({
    required this.totalTickets,
    required this.ticketsNouveau,
    required this.tauxResolution,
    required this.tempsMoyenMinutes,
    required this.ticketsCall,
    required this.ticketsEmail,
    required this.ticketsWeb,
    required this.ticketsEnCours,
    required this.ticketsResolu,
    required this.ticketsFerme,
    required this.decisionsAiFavorable,
    required this.decisionsAiDefavorable,
    required this.decisionsAiEtude,
  });

  factory KpiResponse.fromJson(Map<String, dynamic> json) {
    return KpiResponse(
      totalTickets: json['totalTickets'] ?? 0,
      ticketsNouveau: json['ticketsNouveau'] ?? 0,
      tauxResolution: (json['tauxResolution'] ?? 0).toDouble(),
      tempsMoyenMinutes: (json['tempsMoyenMinutes'] ?? 0).toDouble(),
      ticketsCall: json['ticketsCall'] ?? 0,
      ticketsEmail: json['ticketsEmail'] ?? 0,
      ticketsWeb: json['ticketsWeb'] ?? 0,
      ticketsEnCours: json['ticketsEnCours'] ?? 0,
      ticketsResolu: json['ticketsResolu'] ?? json['ticketsResolus'] ?? 0,
      ticketsFerme: json['ticketsFerme'] ?? 0,
      decisionsAiFavorable: json['decisionsAiFavorable'] ?? 0,
      decisionsAiDefavorable: json['decisionsAiDefavorable'] ?? 0,
      decisionsAiEtude: json['decisionsAiEtude'] ?? 0,
    );
  }

  int get totalDecisions => decisionsAiFavorable + decisionsAiDefavorable + decisionsAiEtude;
}

/// Comparaison période-sur-période — mirrors KpiComparison.java
class KpiComparison {
  final KpiResponse current;
  final KpiResponse previous;
  final double deltaTickets;
  final double deltaResolution;
  final double deltaTempsMoyen;
  final double deltaDecisions;
  final double deltaFavorable;

  KpiComparison({
    required this.current,
    required this.previous,
    required this.deltaTickets,
    required this.deltaResolution,
    required this.deltaTempsMoyen,
    required this.deltaDecisions,
    required this.deltaFavorable,
  });

  factory KpiComparison.fromJson(Map<String, dynamic> json) {
    return KpiComparison(
      current: KpiResponse.fromJson(json['current'] ?? {}),
      previous: KpiResponse.fromJson(json['previous'] ?? {}),
      deltaTickets: (json['deltaTickets'] ?? 0).toDouble(),
      deltaResolution: (json['deltaResolution'] ?? 0).toDouble(),
      deltaTempsMoyen: (json['deltaTempsMoyen'] ?? 0).toDouble(),
      deltaDecisions: (json['deltaDecisions'] ?? 0).toDouble(),
      deltaFavorable: (json['deltaFavorable'] ?? 0).toDouble(),
    );
  }
}

class HourlyData {
  final String hour;
  final int count;

  HourlyData({required this.hour, required this.count});

  factory HourlyData.fromJson(Map<String, dynamic> json) {
    return HourlyData(
      hour: json['hour'] ?? json['date'] ?? '',
      count: json['count'] ?? json['totalTickets'] ?? 0,
    );
  }
}

class DailyStat {
  final String date;
  final int totalTickets;
  final int ticketsResolus;
  final int decisionsFavorable;
  final int decisionsDefavorable;
  final int decisionsEtude;
  final int ticketsCall;
  final int ticketsEmail;
  final int ticketsWeb;

  DailyStat({
    required this.date,
    required this.totalTickets,
    required this.ticketsResolus,
    required this.decisionsFavorable,
    required this.decisionsDefavorable,
    required this.decisionsEtude,
    required this.ticketsCall,
    required this.ticketsEmail,
    required this.ticketsWeb,
  });

  factory DailyStat.fromJson(Map<String, dynamic> json) {
    return DailyStat(
      date: json['date'] ?? '',
      totalTickets: json['totalTickets'] ?? 0,
      ticketsResolus: json['ticketsResolus'] ?? 0,
      decisionsFavorable: json['decisionsFavorable'] ?? 0,
      decisionsDefavorable: json['decisionsDefavorable'] ?? 0,
      decisionsEtude: json['decisionsEtude'] ?? 0,
      ticketsCall: json['ticketsCall'] ?? 0,
      ticketsEmail: json['ticketsEmail'] ?? 0,
      ticketsWeb: json['ticketsWeb'] ?? 0,
    );
  }
}
