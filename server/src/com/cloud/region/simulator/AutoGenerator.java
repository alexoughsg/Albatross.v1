package com.cloud.region.simulator;

public class AutoGenerator {

    private DomainLocalGenerator domainGenerator = new DomainLocalGenerator();
    private AccountLocalGenerator accountGenerator = new AccountLocalGenerator();
    private UserLocalGenerator userGenerator = new UserLocalGenerator();

    private DomainLocalGeneratorEvent domainGeneratorEvent = new DomainLocalGeneratorEvent();
    private AccountLocalGeneratorEvent accountGeneratorEvent = new AccountLocalGeneratorEvent();
    private UserLocalGeneratorEvent userGeneratorEvent = new UserLocalGeneratorEvent();

    public AutoGenerator()
    {
    }

    public void generate()
    {
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
                //domainGenerator.remove(null);
                //accountGenerator.remove(null);
                //userGenerator.remove(null);
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
                //domainGeneratorEvent.remove(null);
                accountGeneratorEvent.remove(null);
                userGeneratorEvent.remove(null);
                break;
        }
    }
}