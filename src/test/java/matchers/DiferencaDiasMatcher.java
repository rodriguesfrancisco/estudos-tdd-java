package matchers;

import br.ce.wcaquino.utils.DataUtils;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeMatcher;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class DiferencaDiasMatcher extends TypeSafeMatcher<Date> {

    private Integer dias;

    public DiferencaDiasMatcher(Integer dias) {
        this.dias = dias;
    }

    @Override
    protected boolean matchesSafely(Date date) {
        return DataUtils.isMesmaData(date, DataUtils.obterDataComDiferencaDias(dias));
    }

    public void describeTo(Description description) {
        Date dataEsperada = DataUtils.obterDataComDiferencaDias(dias);
        DateFormat format = new SimpleDateFormat("dd/MM/YYY");
        description.appendText(format.format(dataEsperada));
    }
}
