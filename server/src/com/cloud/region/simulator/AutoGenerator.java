package com.cloud.region.simulator;

public class AutoGenerator {

    /*private static final ConfigKey<Integer> FullScanInterval = new ConfigKey<Integer>("Advanced", Integer.class, "full.scan.interval.region.commands", "1",
            "The full scan with remote regions will occur if the last time is more than minutes of the given number. Default value is 1 minute.", true);

    private static final ConfigKey<Boolean> RunAutoGeneration = new ConfigKey<Boolean>("Advanced", Boolean.class, "auto.generation.region.commands", "false",
            "If this is true, automatic domain/account/user create/delete/update are processed. Default value is false.", true);*/

    private DomainLocalGenerator domainGenerator = new DomainLocalGenerator();
    private AccountLocalGenerator accountGenerator = new AccountLocalGenerator();
    private UserLocalGenerator userGenerator = new UserLocalGenerator();

    private DomainLocalGeneratorEvent domainGeneratorEvent = new DomainLocalGeneratorEvent();
    private AccountLocalGeneratorEvent accountGeneratorEvent = new AccountLocalGeneratorEvent();
    private UserLocalGeneratorEvent userGeneratorEvent = new UserLocalGeneratorEvent();

    //private Date lastGenerated;

    public AutoGenerator()
    {
        //this.lastGenerated = null;
    }

    /*private boolean timeToGeneration()
    {
        if (lastGenerated == null)   return true;

        long time1 = lastGenerated.getTime();
        long time2 = (new Date()).getTime();
        long diff = time2 - time1;
        long secondInMillis = 1000;
        long elapsedSeconds = diff / secondInMillis;
        return elapsedSeconds > FullScanInterval.value() * 30;
    }*/

    public void generate()
    {
        //if (!RunAutoGeneration.value()) return;

        //if (!timeToGeneration())    return;

        //int randNumber = domainGenerator.generateRandNumber(6);
        int randNumber = domainGenerator.generateRandNumber(18);
        switch (randNumber)
        {
            case 0:
                domainGenerator.create();
                accountGenerator.create();
                userGenerator.create();
                break;
            case 1:
                domainGenerator.update(null);
                accountGenerator.update(null);
                userGenerator.update(null);
                break;
            case 2:
            case 3:
                accountGenerator.disable(null);
                userGenerator.disable(null);
                break;
            case 4:
            case 5:
                accountGenerator.lock(null);
                userGenerator.lock(null);
                break;
            case 6:
            case 7:
                accountGenerator.enable(null);
                userGenerator.enable(null);
                break;
            case 8:
                domainGenerator.remove(null);
                accountGenerator.remove(null);
                userGenerator.remove(null);
                break;
            case 9:
                domainGeneratorEvent.create();
                accountGeneratorEvent.create();
                userGeneratorEvent.create();
                break;
            case 10:
                domainGeneratorEvent.update(null);
                accountGeneratorEvent.update(null);
                userGeneratorEvent.update(null);
                break;
            case 11:
            case 12:
                accountGeneratorEvent.disable(null);
                userGeneratorEvent.disable(null);
                break;
            case 13:
            case 14:
                accountGeneratorEvent.lock(null);
                userGeneratorEvent.lock(null);
                break;
            case 15:
            case 16:
                accountGeneratorEvent.enable(null);
                userGeneratorEvent.enable(null);
                break;
            case 17:
                domainGeneratorEvent.remove(null);
                accountGeneratorEvent.remove(null);
                userGeneratorEvent.remove(null);
                break;
        }
    }
}