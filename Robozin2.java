package robozin2;

import robocode.*;
import robocode.util.Utils;
import java.awt.*;
import java.awt.geom.*;
import java.util.*;

public class Robozin2 extends AdvancedRobot {

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
        // >>> Estética: corpo preto e branco com detalhes vermelhos no canhão
        setBodyColor(Color.black);
        setRadarColor(Color.white);
        setScanColor(Color.white);
        setBulletColor(Color.red);
        setGunColor(Color.red);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            turnRadarRight(360);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // >>> Seleção de alvo mais próximo
        if (alvoAtual == null || e.getDistance() < distanciaAlvoAtual - 50 || e.getName().equals(alvoAtual)) {
            alvoAtual = e.getName();
            distanciaAlvoAtual = e.getDistance();
        } else return;

        // >>> Detecta tiro inimigo e cria onda
        double energiaAtual = e.getEnergy();
        double delta = inimigoEnergia - energiaAtual;
        if (delta > 0.1 && delta <= 3.0) {
            ondasInimigas.add(new Wave(getX(), getY(), getTime(), delta, getHeadingRadians() + Math.toRadians(e.getBearing())));
        }
        inimigoEnergia = energiaAtual;

        // >>> Movimento evasivo com Wave Surfing
        surfWaves();

        // >>> Mira com aprendizado (GuessFactor Targeting)
        dispararComGuessFactor(e);

        // >>> Radar lock
        double absBearing = getHeadingRadians() + Math.toRadians(e.getBearing());
        setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2);
    }

    // >>> Sistema de mira com aprendizado
    private void dispararComGuessFactor(ScannedRobotEvent e) {
        double absBearing = getHeadingRadians() + Math.toRadians(e.getBearing());
        double distancia = e.getDistance();
        if (distancia > 600) return; // Economiza energia

        double potencia = calcularPotenciaTiro(distancia);
        double velocidadeBala = 20 - 3 * potencia;
        int index = 0;

        // >>> Predição via estatística
        double[] stats = guessFactors[index];
        int melhorBin = stats.length / 2;
        for (int i = 0; i < stats.length; i++) {
            if (stats[i] > stats[melhorBin]) melhorBin = i;
        }

        double guessFactor = (melhorBin - (BINS - 1) / 2.0) / ((BINS - 1) / 2.0);
        double direcao = Math.signum(e.getVelocity());
        double ajuste = direcao * guessFactor * getMaxEscapeAngle(velocidadeBala);
        double anguloTiro = absBearing + ajuste;

        setTurnGunRightRadians(Utils.normalRelativeAngle(anguloTiro - getGunHeadingRadians()));
        if (getGunHeat() == 0 && Math.abs(Utils.normalRelativeAngle(anguloTiro - getGunHeadingRadians())) < Math.toRadians(15)) {
            setFire(potencia);
        }
    }

    private double calcularPotenciaTiro(double distancia) {
        if (distancia < 300) return 3;
        else if (distancia < 500) return 2;
        else return 1;
    }

    // >>> Movimento evasivo baseado em risco
    private void surfWaves() {
        if (ondasInimigas.isEmpty()) return;

        Wave onda = ondasInimigas.get(0); // Analisa a mais próxima
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

    // >>> Quando acertado, registra onde foi atingido
    public void onHitByBullet(HitByBulletEvent e) {
        for (Wave onda : ondasInimigas) {
            if (onda.corresponde(e)) {
                int index = 0;
                // >>> Corrige posição do impacto
                double anguloImpacto = e.getHeadingRadians();
                double impactoX = getX() - Math.sin(anguloImpacto) * 20;
                double impactoY = getY() - Math.cos(anguloImpacto) * 20;
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

    private boolean posicaoDentroDoCampo(double x, double y) {
        return x > margemCampo && x < getBattleFieldWidth() - margemCampo
            && y > margemCampo && y < getBattleFieldHeight() - margemCampo;
    }

    private double getMaxEscapeAngle(double velocidadeBala) {
        return Math.asin(8 / velocidadeBala);
    }

    // >>> Classe auxiliar: onda de tiro inimigo
    class Wave {
        double x, y, tempo, velocidade, angulo;
        Wave(double x, double y, double tempo, double energia, double angulo) {
            this.x = x;
            this.y = y;
            this.tempo = tempo;
            this.velocidade = 20 - 3 * energia;
            this.angulo = angulo;
        }

        boolean corresponde(HitByBulletEvent e) {
            double distancia = Point2D.distance(x, y, getX(), getY());
            return Math.abs((getTime() - tempo) * velocidade - distancia) < 50;
        }

        double getRisco(double px, double py) {
            return 1.0 / (Point2D.distance(px, py, getX(), getY()) + 1);
        }

        int getBin(double hitX, double hitY, int bins) {
            double anguloImpacto = Math.atan2(hitX - x, hitY - y);
            double offset = Utils.normalRelativeAngle(anguloImpacto - angulo);
            double maxEscape = getMaxEscapeAngle(velocidade);
            int bin = (int) ((offset / maxEscape) * ((bins - 1) / 2.0) + (bins - 1) / 2.0);
            return Math.max(0, Math.min(bins - 1, bin));
        }
    }
}
