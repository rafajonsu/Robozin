package Robozin;

import robocode.*;
import robocode.util.Utils;
import java.awt.*;
import java.awt.geom.Point2D;

public class Robozin extends AdvancedRobot {

    double inimigoEnergia = 100;
    int direcaoMovimento = 1;

    final double distanciaMinima = 150;
    final double distanciaMaxima = 400;
    final double margemCampo = 50;  // Distância de segurança da parede

    public void run() {
        setBodyColor(Color.red);
        setGunColor(Color.black);
        setRadarColor(Color.green);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            turnRadarRight(360);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double energiaAtualInimigo = e.getEnergy();
        double variacaoEnergia = inimigoEnergia - energiaAtualInimigo;

        // >>> Detecta disparo e aplica dodge com Wall Smoothing
        if (variacaoEnergia > 0 && variacaoEnergia <= 3.0) {
            direcaoMovimento *= -1;
            executarMovimentoComSmoothing(e);
        }

        inimigoEnergia = energiaAtualInimigo;

        // >>> Controle de distância segura
        double distancia = e.getDistance();
        if (distancia < distanciaMinima) {
            setBack(distanciaMinima - distancia + 50);
        } else if (distancia > distanciaMaxima) {
            setAhead(distancia - distanciaMaxima - 50);
        }

        // >>> Mira com Predição Linear
        double velocidadeInimigo = e.getVelocity();
        double anguloMovimentoInimigo = Math.toRadians(getHeading() + e.getBearing());
        double velocidadeX = Math.sin(anguloMovimentoInimigo) * velocidadeInimigo;
        double velocidadeY = Math.cos(anguloMovimentoInimigo) * velocidadeInimigo;

        double posicaoInimigoX = getX() + Math.sin(Math.toRadians(getHeading() + e.getBearing())) * distancia;
        double posicaoInimigoY = getY() + Math.cos(Math.toRadians(getHeading() + e.getBearing())) * distancia;

        double tempoBala;
        double potenciaTiro = calcularPotenciaTiro(distancia);
        double velocidadeBala = 20 - 3 * potenciaTiro;

        double distanciaPrevistaX = posicaoInimigoX;
        double distanciaPrevistaY = posicaoInimigoY;

        for (tempoBala = 0; tempoBala * velocidadeBala < Point2D.distance(getX(), getY(), distanciaPrevistaX, distanciaPrevistaY); tempoBala++) {
            distanciaPrevistaX = posicaoInimigoX + velocidadeX * tempoBala;
            distanciaPrevistaY = posicaoInimigoY + velocidadeY * tempoBala;
        }

        double anguloAbsoluto = Math.toDegrees(Math.atan2(distanciaPrevistaX - getX(), distanciaPrevistaY - getY()));
        double ajusteCanhao = Utils.normalRelativeAngleDegrees(anguloAbsoluto - getGunHeading());
        setTurnGunRight(ajusteCanhao);

        if (getGunHeat() == 0 && Math.abs(ajusteCanhao) < 15) {
            fire(potenciaTiro);
        }

        double ajusteRadar = Utils.normalRelativeAngleDegrees(anguloAbsoluto - getRadarHeading());
        setTurnRadarRight(2 * ajusteRadar);
    }

    private double calcularPotenciaTiro(double distancia) {
        if (distancia < 200) {
            return 3;
        } else if (distancia < 500) {
            return 2;
        } else {
            return 1;
        }
    }

    // >>> Função para movimentação com Wall Smoothing
    private void executarMovimentoComSmoothing(ScannedRobotEvent e) {
        double anguloAlvo = getHeading() + e.getBearing() + 90 - (15 * direcaoMovimento);
        double anguloSuavizado = calcularWallSmoothing(anguloAlvo);
        setTurnRight(Utils.normalRelativeAngleDegrees(anguloSuavizado - getHeading()));
        setAhead((100 + Math.random() * 50) * direcaoMovimento);
    }

    private double calcularWallSmoothing(double anguloDesejado) {
        double novoX, novoY;
        double ajuste = 0;
        double incremento = 5;  // Quanto ajustar o ângulo por tentativa

        do {
            double anguloEmRad = Math.toRadians(anguloDesejado + ajuste);
            novoX = getX() + Math.sin(anguloEmRad) * 100;
            novoY = getY() + Math.cos(anguloEmRad) * 100;

            ajuste += incremento * direcaoMovimento;

            // Se dentro dos limites do campo, retorna o ângulo
        } while (!posicaoDentroDoCampo(novoX, novoY) && Math.abs(ajuste) < 90);

        return anguloDesejado + ajuste;
    }

    private boolean posicaoDentroDoCampo(double x, double y) {
        return x > margemCampo && x < getBattleFieldWidth() - margemCampo
                && y > margemCampo && y < getBattleFieldHeight() - margemCampo;
    }

    public void onHitWall(HitWallEvent e) {
        direcaoMovimento *= -1;
        setBack(100);
    }
}
