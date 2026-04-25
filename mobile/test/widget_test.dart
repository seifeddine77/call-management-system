import 'package:flutter_test/flutter_test.dart';
import 'package:autoassist_mobile/main.dart';

void main() {
  testWidgets('App should build', (WidgetTester tester) async {
    await tester.pumpWidget(const AutoAssistApp());
    expect(find.byType(AutoAssistApp), findsOneWidget);
  });
}
