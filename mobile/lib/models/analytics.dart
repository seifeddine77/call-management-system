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

class OperatorPerformance {
  final String operateurId;
  final String? operateurName;
  final int ticketsTraites;
  final int ticketsEnCours;
  final int ticketsResolu;
  final double tauxResolution;
  final double tempsMoyenMinutes;
  final int ticketsCall;
  final int ticketsEmail;
  final int ticketsWeb;
  final int scoreComposite;
  final double slaCompliance;

  OperatorPerformance({
    required this.operateurId,
    this.operateurName,
    required this.ticketsTraites,
    required this.ticketsEnCours,
    required this.ticketsResolu,
    required this.tauxResolution,
    required this.tempsMoyenMinutes,
    required this.ticketsCall,
    required this.ticketsEmail,
    required this.ticketsWeb,
    required this.scoreComposite,
    required this.slaCompliance,
  });

  factory OperatorPerformance.fromJson(Map<String, dynamic> json) {
    return OperatorPerformance(
      operateurId: json['operateurId'] ?? '',
      operateurName: json['operateurName'],
      ticketsTraites: json['ticketsTraites'] ?? 0,
      ticketsEnCours: json['ticketsEnCours'] ?? 0,
      ticketsResolu: json['ticketsResolu'] ?? json['ticketsResolus'] ?? 0,
      tauxResolution: (json['tauxResolution'] ?? 0).toDouble(),
      tempsMoyenMinutes: (json['tempsMoyenMinutes'] ?? 0).toDouble(),
      ticketsCall: json['ticketsCall'] ?? 0,
      ticketsEmail: json['ticketsEmail'] ?? 0,
      ticketsWeb: json['ticketsWeb'] ?? 0,
      scoreComposite: json['scoreComposite'] ?? 0,
      slaCompliance: (json['slaCompliance'] ?? 0).toDouble(),
    );
  }
}

class SupervisionAlert {
  final String type;
  final String severity;
  final String message;
  final int count;

  SupervisionAlert({
    required this.type,
    required this.severity,
    required this.message,
    required this.count,
  });

  factory SupervisionAlert.fromJson(Map<String, dynamic> json) {
    return SupervisionAlert(
      type: json['type'] ?? '',
      severity: json['severity'] ?? '',
      message: json['message'] ?? '',
      count: json['count'] ?? 0,
    );
  }
}

class ChatbotFeedback {
  final String question;
  final String answerPreview;
  final String rating;
  final String groupName;
  final String? createdAt;

  ChatbotFeedback({
    required this.question,
    required this.answerPreview,
    required this.rating,
    required this.groupName,
    this.createdAt,
  });

  factory ChatbotFeedback.fromJson(Map<String, dynamic> json) {
    return ChatbotFeedback(
      question: json['question'] ?? '',
      answerPreview: json['answer_preview'] ?? '',
      rating: json['rating'] ?? 'positive',
      groupName: json['group_name'] ?? '',
      createdAt: json['created_at'],
    );
  }
}

class ChatbotStats {
  final int totalFeedback;
  final int positive;
  final int negative;
  final String satisfactionRate;
  final int dbPersisted;
  final List<ChatbotFeedback> recentFeedback;

  ChatbotStats({
    required this.totalFeedback,
    required this.positive,
    required this.negative,
    required this.satisfactionRate,
    required this.dbPersisted,
    required this.recentFeedback,
  });

  factory ChatbotStats.fromJson(Map<String, dynamic> json) {
    var list = json['recent_feedback'] as List?;
    List<ChatbotFeedback> feedbackList = list != null
        ? list.map((i) => ChatbotFeedback.fromJson(Map<String, dynamic>.from(i as Map))).toList()
        : [];
    return ChatbotStats(
      totalFeedback: json['total_feedback'] ?? 0,
      positive: json['positive'] ?? 0,
      negative: json['negative'] ?? 0,
      satisfactionRate: json['satisfaction_rate'] ?? '0%',
      dbPersisted: json['db_persisted'] ?? 0,
      recentFeedback: feedbackList,
    );
  }
}

