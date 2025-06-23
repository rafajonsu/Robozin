package meu_pacote;

import robocode.*;
import robocode.util.Utils;

public class Robozin extends AdvancedRobot {

    double inimigoEnergia = 100;   
    int direcao = 1;               

    public void run() {
        setAdjustGunForRobotTurn(true);     
        setAdjustRadarForGunTurn(true);     

        while (true) {
            turnRadarRight(360);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        double novaEnergia = e.getEnergy();
        double diffEnergia = inimigoEnergia - novaEnergia;

        //Detecta disparo e desvia random
        if (diffEnergia > 0 && diffEnergia <= 3.0) {
        
            if (Math.random() > 0.5) {
                direcao *= -1;
            }
            double angLat = e.getBearing() + 90;
            setTurnRight(angLat);
            setAhead((100 + Math.random() * 50) * direcao);
        }

        inimigoEnergia = novaEnergia;

        double angAbs = getHeading() + e.getBearing();
        double angGun = Utils.normalRelativeAngleDegrees(angAbs - getGunHeading());
        setTurnGunRight(angGun);

        //Atira se arma fria
        if (getGunHeat() == 0 && Math.abs(angGun) < 10) {
            fire(1.5);
        }

        //Trava o radar no inimigo
        double angRadar = Utils.normalRelativeAngleDegrees(angAbs - getRadarHeading());
        setTurnRadarRight(2 * angRadar);
    }
}