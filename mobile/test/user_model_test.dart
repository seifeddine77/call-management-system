import 'package:flutter_test/flutter_test.dart';
import 'package:autoassist_mobile/models/user.dart';

void main() {
  group('User Model Tests', () {
    test('should parse user from JSON correctly', () {
      final json = {
        'id': 'u1',
        'email': 'operator@test.com',
        'nom': 'Rezgui',
        'prenom': 'Seif',
        'telephone': '+216123456',
        'role': 'OPERATOR',
        'agenceId': 'a1',
        'agenceName': 'Agence Tunis',
        'actif': true,
        'planning': 'LUN-VEN 8h-17h',
        'createdAt': '2026-06-25T12:00:00Z',
        'lastLogin': '2026-06-25T14:00:00Z'
      };

      final user = User.fromJson(json);

      expect(user.id, 'u1');
      expect(user.email, 'operator@test.com');
      expect(user.nom, 'Rezgui');
      expect(user.prenom, 'Seif');
      expect(user.fullName, 'Seif Rezgui');
      expect(user.initials, 'SR');
      expect(user.role, 'OPERATOR');
      expect(user.agenceId, 'a1');
      expect(user.agenceName, 'Agence Tunis');
      expect(user.actif, true);
      expect(user.planning, 'LUN-VEN 8h-17h');
    });

    test('should return correct initials', () {
      final user = User(
        id: '1',
        email: 'test@email.com',
        nom: 'Rezgui',
        prenom: 'Seif Eddine',
        telephone: '',
        role: 'OPERATOR',
        actif: true,
      );

      expect(user.initials, 'SR');
    });

    test('should perform copyWith correctly', () {
      final user = User(
        id: '1',
        email: 'test@email.com',
        nom: 'Rezgui',
        prenom: 'Seif',
        telephone: '',
        role: 'OPERATOR',
        actif: true,
      );

      final updatedUser = user.copyWith(
        email: 'new@email.com',
        actif: false,
      );

      expect(updatedUser.id, '1');
      expect(updatedUser.email, 'new@email.com');
      expect(updatedUser.actif, false);
      expect(updatedUser.nom, 'Rezgui');
    });
  });
}
