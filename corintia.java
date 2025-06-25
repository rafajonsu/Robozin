package corintia;

import robocode.*;
import robocode.util.Utils;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

public class corintia extends AdvancedRobot {

    // >>> Estado e parâmetros
    double inimigoEnergia = 100;
    int direcaoMovimento = 1;
    final double margemCampo = 60;
    final int BINS = 31;
    double[][] guessFactors = new double[1][BINS];
    ArrayList<Wave> ondasInimigas = new ArrayList<>();
    String alvoAtual = null;
    double distanciaAlvoAtual = Double.MAX_VALUE;

    public void run() {
        //*Estética visual do robô*
        setBodyColor(Color.black);
        setRadarColor(Color.white);
        setScanColor(Color.white);
        setBulletColor(Color.yellow);
        setGunColor(Color.red);

        //*Evita que rotação do corpo afete radar ou canhão*
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        //*Inicializa bin central com peso neutro para evitar erro no início*
        guessFactors[0][BINS / 2] = 1.0;

        while (true) {
            turnRadarRight(360); //*Mantém radar girando continuamente*
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        //*Seleciona o inimigo mais próximo como alvo prioritário*
        if (alvoAtual == null || e.getDistance() < distanciaAlvoAtual - 50 || e.getName().equals(alvoAtual)) {
            alvoAtual = e.getName();
            distanciaAlvoAtual = e.getDistance();
        } else return;

        //*Detecta tiro inimigo pela queda de energia*
        double energiaAtual = e.getEnergy();
        double delta = inimigoEnergia - energiaAtual;
        if (delta > 0.1 && delta <= 3.0) {
            ondasInimigas.add(new Wave(getX(), getY(), getTime(), delta, getHeadingRadians() + Math.toRadians(e.getBearing())));
        }
        inimigoEnergia = energiaAtual;

        surfWaves(); //*Executa movimentação evasiva baseada em risco*
        dispararComGuessFactor(e); //*Dispara usando estatística adaptativa*

        //*Radar lock no inimigo*
        double absBearing = getHeadingRadians() + Math.toRadians(e.getBearing());
        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);
    }

    // >>> Sistema de mira adaptativo com aprendizado
    private void dispararComGuessFactor(ScannedRobotEvent e) {
        double absBearing = getHeadingRadians() + Math.toRadians(e.getBearing());
        double distancia = e.getDistance();
        if (distancia > 600) return; //*Economiza energia em alvos distantes demais*

        double potencia = calcularPotenciaTiro(distancia);
        double velocidadeBala = 20 - 3 * potencia;
        int index = 0;

        //*Predição baseada em estatísticas acumuladas*
        double[] stats = guessFactors[index];
        int melhorBin = stats.length / 2;
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] > stats[melhorBin]) melhorBin = i;
        }

        double guessFactor = (melhorBin - (BINS - 1) / 2.0) / ((BINS - 1) / 2.0);
        double direcao = Math.signum(e.getVelocity());
        double velocidadeInimigo = Math.abs(e.getVelocity()); //*Considera velocidade do inimigo no ajuste*
        double ajuste = direcao * guessFactor * getMaxEscapeAngle(velocidadeBala) * (velocidadeInimigo / 8.0);
        double anguloTiro = absBearing + ajuste;

        setTurnGunRightRadians(Utils.normalRelativeAngle(anguloTiro - getGunHeadingRadians()));

        //*Controle dinâmico da margem de erro com base na distância*
        double margemErro = Math.toRadians(Math.min(15, 500 / distancia));
        if (getGunHeat() == 0 && Math.abs(Utils.normalRelativeAngle(anguloTiro - getGunHeadingRadians())) < margemErro) {
            setFire(potencia);
        }
    }

    private double calcularPotenciaTiro(double distancia) {
        if (distancia < 300) return 3;
        else if (distancia < 500) return 2;
        else return 1;
    }

    // >>> Movimento evasivo baseado em risco das ondas detectadas
    private void surfWaves() {
        if (ondasInimigas.isEmpty()) return;

        Wave onda = ondasInimigas.get(0); //*Analisa a onda mais próxima*
        double menorRisco = Double.POSITIVE_INFINITY;
        double melhorAngulo = 0;

        for (int i = -90; i <= 90; i += 15) {
            double angulo = Math.toRadians(i);
            double direcao = getHeadingRadians() + angulo;
            double destinoX = getX() + Math.sin(direcao) * 100;
            double destinoY = getY() + Math.cos(direcao) * 100;

            if (!posicaoDentroDoCampo(destinoX, destinoY)) continue;

            double risco = onda.getRisco(destinoX, destinoY);
            if (risco < menorRisco) {
                menorRisco = risco;
                melhorAngulo = direcao;
            }
        }

        setTurnRightRadians(Utils.normalRelativeAngle(melhorAngulo - getHeadingRadians()));
        setAhead(100);
    }

    // >>> Quando atingido por bala inimiga
    public void onHitByBullet(HitByBulletEvent e) {
        for (Wave onda : ondasInimigas) {
            if (onda.corresponde(e)) {
                int index = 0;

                //*Ajuste da posição estimada do impacto*
                double impactoX = getX();
                double impactoY = getY();
                int bin = onda.getBin(impactoX, impactoY, BINS);
                guessFactors[index][bin] += 1;
                ondasInimigas.remove(onda);
                break;
            }
        }
    }

    public void onRobotDeath(RobotDeathEvent e) {
        if (e.getName().equals(alvoAtual)) {
            alvoAtual = null;
            distanciaAlvoAtual = Double.MAX_VALUE;
        }
    }

    //*Verifica se posição está dentro da área de batalha com margem de segurança*
    private boolean posicaoDentroDoCampo(double x, double y) {
        return x > margemCampo && x < getBattleFieldWidth() - margemCampo
            && y > margemCampo && y < getBattleFieldHeight() - margemCampo;
    }

    private double getMaxEscapeAngle(double velocidadeBala) {
        return Math.asin(8 / velocidadeBala);
    }

    // >>> Classe auxiliar que representa uma onda inimiga (tiro)
    class Wave {
        double x, y, tempo, velocidade, angulo;

        Wave(double x, double y, double tempo, double energia, double angulo) {
            this.x = x;
            this.y = y;
            this.tempo = tempo;
            this.velocidade = 20 - 3 * energia;
            this.angulo = angulo;
        }

        //*Verifica se esta onda corresponde ao impacto recebido*
        boolean corresponde(HitByBulletEvent e) {
            double distancia = Point2D.distance(x, y, getX(), getY());
            return Math.abs((getTime() - tempo) * velocidade - distancia) < 50;
        }

        //*Calcula risco de ser atingido em uma coordenada hipotética*
        double getRisco(double px, double py) {
            return 1.0 / (Point2D.distance(px, py, getX(), getY()) + 1);
        }

        //*Determina bin baseado no ponto onde a bala nos atingiu*
        int getBin(double hitX, double hitY, int bins) {
            double anguloImpacto = Math.atan2(hitX - x, hitY - y);
            double offset = Utils.normalRelativeAngle(anguloImpacto - angulo);
            double maxEscape = getMaxEscapeAngle(velocidade);
            int bin = (int) ((offset / maxEscape) * ((bins - 1) / 2.0) + (bins - 1) / 2.0);
            return Math.max(0, Math.min(bins - 1, bin));
        }
    }
}
