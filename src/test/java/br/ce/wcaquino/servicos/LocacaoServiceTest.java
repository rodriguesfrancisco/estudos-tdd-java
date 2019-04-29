package br.ce.wcaquino.servicos;

import br.ce.wcaquino.daos.LocacaoDAO;
import br.ce.wcaquino.daos.LocacaoDAOFake;
import br.ce.wcaquino.entidades.Filme;
import br.ce.wcaquino.entidades.Locacao;
import br.ce.wcaquino.entidades.Usuario;
import br.ce.wcaquino.exceptions.FilmeSemEstoqueException;
import br.ce.wcaquino.exceptions.LocadoraException;
import br.ce.wcaquino.servicos.LocacaoService;
import br.ce.wcaquino.utils.DataUtils;
import builders.FilmeBuilder;
import builders.LocacaoBuilder;
import builders.UsuarioBuilder;
import matchers.DiferencaDiasMatcher;
import matchers.MatchersProprios;
import org.hamcrest.CoreMatchers;
import org.junit.*;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExpectedException;
import org.mockito.*;

import java.lang.reflect.Array;
import java.util.*;

import static br.ce.wcaquino.utils.DataUtils.*;
import static builders.FilmeBuilder.*;
import static builders.LocacaoBuilder.*;
import static builders.UsuarioBuilder.*;
import static matchers.MatchersProprios.*;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class LocacaoServiceTest {

    @InjectMocks
    private LocacaoService locacaoService;

    @Mock
    private EmailService emailService;
    @Mock
    private SPCService spc;
    @Mock
    private LocacaoDAO dao;

    @Rule
    public ErrorCollector error = new ErrorCollector();

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setup(){
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void deveAlugarFilme() throws Exception{
        Assume.assumeFalse(verificarDiaSemana(new Date(), Calendar.SATURDAY));

        //Cenário
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = Arrays.asList(umFilme().comValor(100.0).agora());

        //Ação
        Locacao locacao = locacaoService.alugarFilmes(usuario, filmes);

        //Verificação
        error.checkThat(isMesmaData(locacao.getDataLocacao(), new Date()), is(true));

        //AssertThat = Verifique que
        error.checkThat(isMesmaData(locacao.getDataRetorno()
                , obterDataComDiferencaDias(1)), is(true));
        error.checkThat(locacao.getValor(), is(equalTo(100.0)));
        error.checkThat(locacao.getDataRetorno(), isHojeComDiferencaDias(1));
        error.checkThat(locacao.getDataLocacao(), isHoje());
    }

    @Test(expected = FilmeSemEstoqueException.class)
    public void naoDeveAlugarFilmeSemEstoque() throws Exception{
        //Cenário
        List<Filme> filmes = Arrays.asList(umFilme().semEstoque().agora());
        Usuario usuario = umUsuario().agora();

        //Ação
        locacaoService.alugarFilmes(usuario, filmes);
    }

    //robusto
    @Test
    public void testLocacao_usuarioVazio() throws FilmeSemEstoqueException{
        //cenario
        List<Filme> filmes = Arrays.asList(umFilme().agora());

        //acao
        try {
            locacaoService.alugarFilmes(null, filmes);
            fail();
        }catch (LocadoraException el){
            assertThat(el.getMessage(), is("Usuário vazio"));
        }
    }

    //novo
    @Test
    public void testLocacao_FilmeVazio() throws FilmeSemEstoqueException, LocadoraException {
        //cenario
        Usuario usuario = umUsuario().agora();

        exception.expect(LocadoraException.class);
        exception.expectMessage("Filmes vazio");

        //acao
        locacaoService.alugarFilmes(usuario, null);

    }

    @Test
    public void deveDevolverNaSegundaAoAlugarNosSabados() throws FilmeSemEstoqueException, LocadoraException {
        //cenario
        Usuario usuario = umUsuario().agora();

        List<Filme> filmes = Arrays.asList(umFilme().agora());

        //acao
        Locacao locacao = locacaoService.alugarFilmes(usuario, filmes);

        //verificacao
        assertThat(locacao.getDataRetorno(), caiEm(Calendar.MONDAY));
        assertThat(locacao.getDataRetorno(), caiNumaSegunda());
    }

    @Test
    public void naoDeveAlugarFilmeParaNegativadoSPC() throws Exception {
        //cenario
        Usuario usuario = umUsuario().agora();
        
        List<Filme> filmes = Arrays.asList(umFilme().agora());

        when(spc.possuiNegativacao(Mockito.any(Usuario.class))).thenReturn(true);

        //acao
        try {
            locacaoService.alugarFilmes(usuario, filmes);
        //verificacao
            fail();
        } catch (LocadoraException e) {
            assertThat(e.getMessage(), is("Usuário Negativado"));
        }

        verify(spc).possuiNegativacao(usuario);
    }

    @Test
    public void deveEnviarEmailParaLocacoesAtrasadas() {
        //cenario
        Usuario usuario = umUsuario().agora();
        Usuario usuario2 = umUsuario().comNome("Usuário em Dia").agora();
        Usuario usuario3 = umUsuario().comNome("Outro atrasado").agora();

        List<Locacao> locacoes = Arrays.asList(
                umLocacao()
                    .atrasada()
                    .comUsuario(usuario)
                    .agora(),
                umLocacao()
                    .comUsuario(usuario2)
                    .agora(),
                umLocacao()
                    .atrasada()
                    .comUsuario(usuario3)
                    .agora(),
                umLocacao()
                    .atrasada()
                    .comUsuario(usuario3)
                    .agora());

        when(dao.obterLocacoesPendentes()).thenReturn(locacoes);
        //acao
        locacaoService.notificarAtrasos();

        //verificacao
        verify(emailService, times(3)).notificarAtraso(Mockito.any(Usuario.class));
        verify(emailService).notificarAtraso(usuario);
        verify(emailService, atLeastOnce()).notificarAtraso(usuario3);
        verify(emailService, never()).notificarAtraso(usuario2);
        verifyNoMoreInteractions(emailService);
    }

    @Test
    public void deveTratarErroNoSpc() throws Exception {
        //cenario
        Usuario usuario = umUsuario().agora();
        List<Filme> filmes = Arrays.asList(umFilme().agora());

        when(spc.possuiNegativacao(usuario)).thenThrow(new Exception("Falha de integração"));

        //verificacao
        exception.expect(LocadoraException.class);
        exception.expectMessage("SPC fora do ar.");

        //acao
        locacaoService.alugarFilmes(usuario, filmes);
    }

    @Test
    public void deveProrrogarLocacao() {
        //cenario
        Locacao locacao = umLocacao().agora();

        //ação
        locacaoService.prorrogarLocacao(locacao, 3);

        //verificação
        ArgumentCaptor<Locacao> argCapt = ArgumentCaptor.forClass(Locacao.class);
        Mockito.verify(dao).salvar(argCapt.capture());
        Locacao locacaoRetornada = argCapt.getValue();

        error.checkThat(locacaoRetornada.getValor(), is(12.0));
        error.checkThat(locacaoRetornada.getDataLocacao(), isHoje());
        error.checkThat(locacaoRetornada.getDataRetorno(), isHojeComDiferencaDias(3));
    }
}
