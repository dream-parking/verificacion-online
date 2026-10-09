import 'package:flutter_test/flutter_test.dart';

import 'package:onboarding/onboarding/application_form.dart';
import 'package:onboarding/signals/interaction.dart';
import 'package:onboarding/signals/signals.dart';

void main() {
  final t0 = DateTime.utc(2026, 10, 7, 10);
  DateTime ms(int n) => t0.add(Duration(milliseconds: n));

  /// Types [text] letter by letter, one keystroke every [every] ms, starting at [from].
  int type(TypingRhythm r, String field, String text, {int from = 0, int every = 200}) {
    for (var i = 1; i <= text.length; i++) {
      r.record(field, text.substring(0, i), ms(from + (i - 1) * every));
    }
    return from + (text.length - 1) * every;
  }

  group('TypingRhythm', () {
    test('one keystroke every 200 ms is 300 characters per minute', () {
      final r = TypingRhythm();
      type(r, 'firstNames', 'Marta Alejandra');
      expect(r.cpm, 300);
    });

    test('with few keystrokes it reports nothing', () {
      final r = TypingRhythm();
      type(r, 'firstNames', 'Ana');
      expect(r.cpm, isNull);
    });

    test('long pauses do not count as typing time', () {
      final r = TypingRhythm();
      final end = type(r, 'firstNames', 'Marta');
      type(r, 'lastNames', 'Rivas', from: end + 30000); // 30 s thinking
      expect(r.cpm, 300);
    });

    test('pasting or autofill does not count as typing', () {
      final r = TypingRhythm();
      r.record('firstNames', 'Marta Alejandra', ms(0));
      expect(r.cpm, isNull);
      type(r, 'lastNames', 'Rivas Cruz', from: 100);
      expect(r.cpm, 300);
    });

    test('deleting adds nothing; the mask dash counts as part of the keystroke', () {
      final r = TypingRhythm();
      final texts = ['0', '04', '048', '0481', '04812', '048123', '0481237', '04812377', '04812377-5'];
      for (var i = 0; i < texts.length; i++) {
        r.record('dui', texts[i], ms(i * 200));
      }
      r.record('dui', '04812377-', ms(texts.length * 200));
      expect(r.cpm, 300);
    });

    test('in characters per second: one keystroke every 200 ms is 5.00', () {
      final r = TypingRhythm();
      type(r, 'firstNames', 'Marta Alejandra');
      expect(r.cps, 5.0);
      expect(TypingRhythm().cps, isNull);
    });

    test('characters per second are rounded to 2 decimals and never go above 50', () {
      final r = TypingRhythm();
      type(r, 'firstNames', 'Marta Alejandra', every: 300);
      expect(r.cps, 3.33);
      final fast = TypingRhythm();
      type(fast, 'firstNames', 'Marta Alejandra', every: 1);
      expect(fast.cps, 50);
    });

    test('never goes above the API maximum', () {
      final r = TypingRhythm();
      type(r, 'firstNames', 'Marta Alejandra', every: 1);
      expect(r.cpm, 2000);
    });
  });

  group('StepTimings', () {
    test('keeps start, end and attempts in the API format', () {
      final t = StepTimings()
        ..start(Screen.welcome, ms(0))
        ..start(Screen.basicData, ms(1000))
        ..attempt(Screen.basicData)
        ..attempt(Screen.basicData)
        ..complete(Screen.basicData, ms(5000))
        ..start(Screen.income, ms(5000));

      expect(t.toJson(), [
        {
          'step': 'BASIC_DATA',
          'startedAt': '2026-10-07T10:00:01.000Z',
          'completedAt': '2026-10-07T10:00:05.000Z',
          'attempts': 2,
        },
        {'step': 'INCOME', 'startedAt': '2026-10-07T10:00:05.000Z', 'attempts': 1},
      ]);
    });

    test('coming back to a step keeps the start and updates the end', () {
      final t = StepTimings()
        ..start(Screen.basicData, ms(0))
        ..complete(Screen.basicData, ms(1000))
        ..start(Screen.basicData, ms(9000))
        ..complete(Screen.basicData, ms(12000));
      final step = t.toJson().single;
      expect(step['startedAt'], '2026-10-07T10:00:00.000Z');
      expect(step['completedAt'], '2026-10-07T10:00:12.000Z');
    });
  });

  test('Signals sends the typing speed of each screen inside its step (VDI-68)', () {
    final s = Signals();
    s.steps
      ..start(Screen.basicData, ms(0))
      ..start(Screen.income, ms(60000))
      ..start(Screen.expectedActivity, ms(90000));
    void typeOn(Screen screen, String field, String text, {required int from, required int every}) {
      for (var i = 1; i <= text.length; i++) {
        s.recordTyping(screen, field, text.substring(0, i), ms(from + (i - 1) * every));
      }
    }

    typeOn(Screen.basicData, 'firstNames', 'Marta Alejandra', from: 0, every: 200);
    typeOn(Screen.income, 'incomeSourceDetail', 'Herencia familiar', from: 60000, every: 400);

    final steps = (s.toJson()['steps'] as List).cast<Map<String, Object?>>();
    expect(steps[0]['typingSpeedCps'], 5.0);
    expect(steps[1]['typingSpeedCps'], 2.5);
    expect(steps[2].containsKey('typingSpeedCps'), isFalse, reason: 'nothing was typed on that screen');
    expect(s.toJson()['typingSpeedCpm'], isNotNull, reason: 'the overall speed is still sent');
  });

  test('Signals includes typing speed and steps when there is data', () {
    final s = Signals();
    expect(s.toJson(), isEmpty);
    type(s.typing, 'firstNames', 'Marta Alejandra');
    s.steps.start(Screen.basicData, ms(0));
    final json = s.toJson();
    expect(json['typingSpeedCpm'], 300);
    expect(json['steps'], hasLength(1));
  });
}
