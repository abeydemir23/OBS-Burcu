package org.example;

import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.json.simple.*;
import org.json.simple.parser.*;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException, IOException, ParseException {
        String credentialFilePath = args[0];
        JSONParser parser = new JSONParser();
        Object obj = parser.parse(new FileReader(credentialFilePath));
        JSONObject jsonObject = (JSONObject) obj;
        String username = (String) jsonObject.get("username");
        String password = (String) jsonObject.get("password");
        Long schedulePeriodMin = (Long) jsonObject.get("schedulePeriodMin");
        Long loginWaitMillis = (Long) jsonObject.get("loginWaitMillis");
        Long adWaitMillis = (Long) jsonObject.get("adWaitMillis");
        Long afterAdQuitMillis = (Long) jsonObject.get("afterAdQuitMillis");
        Long adLoopCount = (Long) jsonObject.get("adLoopCount");

        ScheduledThreadPoolExecutor threadPool;
        threadPool = new ScheduledThreadPoolExecutor(2);
        // WebDriver'ı başlat
        Runnable adWatcher = new AdWatcher(username, password, loginWaitMillis, adWaitMillis, afterAdQuitMillis, adLoopCount, schedulePeriodMin, threadPool);
        ScheduledFuture<?> future = threadPool.scheduleAtFixedRate(adWatcher, 0, schedulePeriodMin, TimeUnit.MINUTES);
    }

    static class AdWatcher implements Runnable {
        private final String username;
        private final String password;
        private final Long loginWaitMillis;
        private final Long adWaitMillis;
        private final Long afterAdQuitMillis;
        private final Long adLoopCount;
        private final Long schedulePeriodMin;
        private ScheduledThreadPoolExecutor executor;
        private final List<WebDriver> drivers = new ArrayList<>();

        public AdWatcher(String username, String password, Long loginWaitMillis,
                         Long adWaitMillis, Long afterAdQuitMillis, Long adLoopCount, Long schedulePeriodMin, ScheduledThreadPoolExecutor executor) {
            this.username = username;
            this.password = password;
            this.loginWaitMillis = loginWaitMillis;
            this.adWaitMillis = adWaitMillis;
            this.afterAdQuitMillis = afterAdQuitMillis;
            this.adLoopCount = adLoopCount;
            this.schedulePeriodMin = schedulePeriodMin;
            this.executor = executor;
        }

        @Override
        public void run() {
            WebDriver driver = null;
            try {
                ChromeOptions op = new ChromeOptions();
                // add muted argument
                op.addArguments("−−mute−audio");
                op.addArguments("--headless");
                driver = new ChromeDriver(op);
                drivers.add(driver);
                watchAd(driver, username, password, loginWaitMillis, adWaitMillis, afterAdQuitMillis, adLoopCount);
            } catch (Exception e) {
                drivers.stream().map(AdWatcher::closeAndQuitWebDriver);
                drivers.clear();
                System.err.println(e.getMessage());
                executor.shutdown();
                executor = new ScheduledThreadPoolExecutor(2);
                executor.scheduleAtFixedRate(this, 0, schedulePeriodMin, TimeUnit.MINUTES);
            }
        }

        private static WebDriver closeAndQuitWebDriver(WebDriver driver) {
            String originalHandle = driver.getWindowHandle();
            return driver.getWindowHandles().stream().filter(handle -> !handle.equals(originalHandle))
                    .map(handle -> {
                                System.out.println("Hatalı Pencere Kapanıyor");
                                driver.switchTo().window(handle);
                                driver.close();
                                return driver;
                            }
                    ).map(webDriver -> {
                                System.out.println("Hatalı WebDriver Kapanıyor");
                                webDriver.switchTo().window(originalHandle).quit();
                                return webDriver;
                            }
                    ).findFirst().get();
        }

        private static void watchAd(WebDriver driver, String username, String password, Long loginWaitMillis,
                                    Long adWaitMillis, Long afterAdQuitMillis, Long adLoopCount) throws InterruptedException {
            try {

                driver.manage().window().maximize();

                driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

                driver.get("https://en.onlinesoccermanager.com/PrivacyNotice?nextUrl=%2FLogin");


                login(driver, username, password, loginWaitMillis);
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
                driver.get("https://en.onlinesoccermanager.com/BusinessClub");
                new WebDriverWait(driver, Duration.ofSeconds(10)).until(ExpectedConditions.urlToBe("https://en.onlinesoccermanager.com/BusinessClub"));
                driver.navigate().refresh();
                String originalHandle = driver.getWindowHandle();
                int startCoin = Integer.parseInt(driver.findElement(By.cssSelector("#balances > div > div.wallet-amount.pull-left.center > div > span")).getText());
                System.out.println("Başlangıç Coin : " + startCoin);
                int currentReturnCoin = watchAds(driver, adWaitMillis, adLoopCount);
                if (currentReturnCoin == 0) {
                    closeAndQuitWebDriver(driver);
                    System.out.println("Reklamlar İzlendi Limit Aşıldığı İçin Kapanıyor Mevcut Boss Coin : " + startCoin);
                    return;
                }
                int gained = currentReturnCoin - startCoin;
                if (gained < 9) {
                    System.out.println("Eksik " + (10 - gained) + "coin tamamlanıyor");
                    for (String handle : driver.getWindowHandles()) {
                        if (!handle.equals(originalHandle)) {
                            driver.switchTo().window(handle);
                            driver.close();
                        }
                    }
                    watchAds(driver, adWaitMillis, (long) (10 - gained));
                    return;
                }


                System.out.println("Reklamlar İzlendi " + afterAdQuitMillis + " ms Sonra Kapanıyor");
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(afterAdQuitMillis));
                for (String handle : driver.getWindowHandles()) {
                    if (!handle.equals(originalHandle)) {
                        driver.switchTo().window(handle);
                        driver.close();
                    }
                }

                driver.switchTo().window(originalHandle);
                driver.navigate().refresh();
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
                WebElement currentCoin = driver.findElement(By.cssSelector("#balances > div > div.wallet-amount.pull-left.center > div > span"));
                System.out.println("Reklamlar bitti Mevcut Boss Coin : " + currentCoin.getText());
                driver.quit();
            } catch (Exception e) {
                if (driver != null) {
                    String currentHandle = driver.getWindowHandle();
                    for (String handle : driver.getWindowHandles()) {
                        if (!handle.equals(currentHandle)) {
                            driver.switchTo().window(handle);
                            driver.close();
                        }
                    }
                    driver.switchTo().window(currentHandle).quit();

                }
                System.out.println("Reklamlar izlenirken hata alındı: " + e.getMessage());
                throw e;
            }
        }

        private static int watchAds(WebDriver driver, Long adWaitMillis, Long adLoopCount) throws InterruptedException {
            for (int i = 0; i < adLoopCount; i++) {
                if (openAd(driver, adWaitMillis)< 0) {
                    return 0;
                }

            }
            driver.navigate().to("https://en.onlinesoccermanager.com/BusinessClub");
            driver.navigate().refresh();
            WebElement currentCoin = driver.findElement(By.cssSelector("#balances > div.wallet-container.bosscoin-wallet.btn-new.btn-success > div.wallet-inner-shadow-overlay"));
            return Integer.parseInt(currentCoin.getText());
        }

        private static int openAd(WebDriver driver, Long adWaitMillis) throws InterruptedException {
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
            WebElement currentCoin = driver.findElement(By.cssSelector("#balances > div > div.wallet-amount.pull-left.center > div > span"));
            System.out.println("Mevcut Boss Coin : " + currentCoin.getText());
            driver.switchTo().newWindow(WindowType.TAB);
            driver.get("https://en.onlinesoccermanager.com/BusinessClub");
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
            WebElement watchAdButton = driver.findElement(By.cssSelector("#body-content > div.row.row-h-sm-600.row-h-md-23.overflow-hidden.theme-stepover-0.businessclub-container > div.col-xs-12.col-h-xs-22.col-h-sm-20.businessclub-rows-container > div > div:nth-child(1) > div"));
            watchAdButton.click();
            System.out.println("Reklam başlatılıyor.");
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
            List<WebElement> adLogo = driver.findElements(By.cssSelector("#aipLogo"));
            if (!adLogo.isEmpty()) {
                System.out.println("Reklam Başarılı bir şekilde açıldı");
            }
            else {

                List<WebElement> elements = driver.findElements(By.cssSelector("#modal-dialog-alert > div.row.row-h-xs-24.overflow-visible.modal-content-container > div > div > div > div.modal-header > h3"));
                if (!elements.isEmpty()) {
                    boolean isLimitReached = elements.get(0).getText().equalsIgnoreCase("Can't show video");
                    if (isLimitReached) {
                        System.out.println("Reklam Limiti doldu çıkılıyor");
                        driver.navigate().refresh();
                        return -1;
                    }
                }

                System.out.println("Reklam açılmadı tekrar deneniyor");
                driver.close();
                openAd(driver, adWaitMillis);
            }
            return 0;
        }

        private static void login(WebDriver driver, String username, String password, Long loginWaitMillis) throws InterruptedException {
            WebElement button = driver.findElement(By.cssSelector("#page-privacynotice > div > div > div:nth-child(2) > div:nth-child(3) > div > button"));
            button.click();

            WebElement loginButton = driver.findElement(By.cssSelector("#page-signup > div.page.content.hidden-before-binding > div.register-information-container.horizontal-center-absolute > div.register-information-block.buttons > button"));
            loginButton.click();


            WebElement usernameField = driver.findElement(By.cssSelector("#manager-name"));
            WebElement passwordField = driver.findElement(By.cssSelector("#password"));
            WebElement actualLoginButton = driver.findElement(By.cssSelector("#login"));
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
            usernameField.sendKeys(username);
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
            passwordField.sendKeys(password);
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
//            actualLoginButton.sendKeys(Keys.ENTER);
            new Actions(driver).moveToElement(actualLoginButton).click().build().perform();
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
            WebDriverWait wdw = new WebDriverWait(driver, Duration.ofMillis(loginWaitMillis));
            boolean until = wdw.until(ExpectedConditions.urlToBe("https://en.onlinesoccermanager.com/ChooseLeague"));
            if (until) {
                System.out.println("Login işlemi başarılı");
                return;
            }
            driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
            List<WebElement> elements = driver.findElements(By.cssSelector("#page-signup > div.page.content.hidden-before-binding > div.register-information-container.horizontal-center-absolute > div.register-information-block.buttons > button"));
            if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                elements.get(0).click();
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
                usernameField = driver.findElement(By.cssSelector("#manager-name"));
                passwordField = driver.findElement(By.cssSelector("#password"));
                actualLoginButton = driver.findElement(By.cssSelector("#login"));
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
                usernameField.sendKeys(username);
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
                passwordField.sendKeys(password);
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
                actualLoginButton.click();
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(loginWaitMillis));
                boolean until1 = wdw.until(ExpectedConditions.urlToBe("https://en.onlinesoccermanager.com/ChooseLeague"));

                if (until1) {
                    System.out.println("Login işlemi başarılı");
                    return;
                } else {
                    System.err.println("Login işlemi başarısız");
                    throw new RuntimeException();
                }
            }
        }
    }


}