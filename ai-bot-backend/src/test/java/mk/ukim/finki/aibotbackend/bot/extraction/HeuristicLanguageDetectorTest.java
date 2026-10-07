package mk.ukim.finki.aibotbackend.bot.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HeuristicLanguageDetectorTest {
    private final HeuristicLanguageDetector detector = new HeuristicLanguageDetector();

    @Test
    void testMacedonianTextScoresHigh() {
        String text = "Денеска во Скопје ќе има сончево време, а навечер се очекува лесно разладување. "
            + "Граѓаните се советуваат да внимаваат на сообраќајот.";

        assertThat(detector.macedonianConfidence(text)).isGreaterThan(0.8);
    }

    @Test
    void testShortMacedonianSentenceScoresHigh() {
        assertThat(detector.macedonianConfidence("Ќе дојдам утре во пет часот.")).isGreaterThan(0.6);
    }

    @Test
    void testLinksMentionsAndHashtagsAreIgnored() {
        String text = "Погледнете ја новата песна https://youtu.be/abc #македонија @mkmuzika ќе ви се допадне!";

        assertThat(detector.macedonianConfidence(text)).isGreaterThan(0.8);
    }

    @Test
    void testSerbianTextScoresLow() {
        String text = "Данас је у Београду сунчано време, а увече се очекује лагано захлађење.";

        assertThat(detector.macedonianConfidence(text)).isLessThan(0.5);
    }

    @Test
    void testBulgarianAndRussianTextScoreLow() {
        String bulgarian = "Днес в София ще бъде слънчево, а вечерта се очаква леко захлаждане.";
        String russian = "Сегодня в Москве будет солнечно, а вечером ожидается небольшое похолодание.";

        assertThat(detector.macedonianConfidence(bulgarian)).isLessThan(0.2);
        assertThat(detector.macedonianConfidence(russian)).isLessThan(0.2);
    }

    @Test
    void testLatinTextIsNotMacedonian() {
        assertThat(detector.macedonianConfidence("Today in Skopje the weather will be sunny.")).isZero();
        assertThat(detector.macedonianConfidence("Denes vo Skopje ke ima sonce.")).isZero();
    }

    @Test
    void testEmptyOrTooShortTextScoresZero() {
        assertThat(detector.macedonianConfidence(null)).isZero();
        assertThat(detector.macedonianConfidence("   ")).isZero();
        assertThat(detector.macedonianConfidence("ок 👍")).isZero();
    }
}
