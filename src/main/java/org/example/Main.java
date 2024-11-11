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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
        Boolean visible = (Boolean) jsonObject.get("visible");

        ScheduledThreadPoolExecutor threadPool
                = new ScheduledThreadPoolExecutor(2);
        // WebDriver'ı başlat
        Runnable adWatcher = new AdWatcher(username, password, loginWaitMillis, adWaitMillis, afterAdQuitMillis, adLoopCount, schedulePeriodMin, visible, threadPool);

        threadPool.scheduleAtFixedRate(adWatcher, 0, schedulePeriodMin, TimeUnit.MINUTES);
    }

    static class AdWatcher implements Runnable {
        private final String username;
        private final String password;
        private final Long loginWaitMillis;
        private final Long adWaitMillis;
        private final Long afterAdQuitMillis;
        private final Long adLoopCount;
        private final Long schedulePeriodMin;
        private final Boolean visible;
        private ScheduledThreadPoolExecutor executor;
        private final List<WebDriver> drivers = new ArrayList<>();

        public AdWatcher(String username, String password, Long loginWaitMillis, Long adWaitMillis, Long afterAdQuitMillis, Long adLoopCount, Long schedulePeriodMin, Boolean visible, ScheduledThreadPoolExecutor executor) {
            this.username = username;
            this.password = password;
            this.loginWaitMillis = loginWaitMillis;
            this.adWaitMillis = adWaitMillis;
            this.afterAdQuitMillis = afterAdQuitMillis;
            this.adLoopCount = adLoopCount;
            this.schedulePeriodMin = schedulePeriodMin;
            this.visible = visible;
            this.executor = executor;
        }

        @Override
        public void run() {
            WebDriver driver = null;
            try {
                ChromeOptions op = new ChromeOptions();
                // add muted argument
                op.addArguments("−−mute−audio");
                if (!visible) {
                    op.addArguments("--headless");

                }
                driver = new ChromeDriver(op);
                drivers.add(driver);
                watchAd(driver, username, password, loginWaitMillis, adWaitMillis, afterAdQuitMillis, adLoopCount);
            } catch (Exception e) {
                for (WebDriver d : drivers) {
                    String originalHandle = d.getWindowHandle();
                    for (String handle : d.getWindowHandles()) {
                        if (!originalHandle.equals(handle)) {
                            d.switchTo().window(handle);
                            d.close();
                        }

                    }
                    d.switchTo().window(originalHandle);
                    d.quit();
                }
                drivers.clear();
                System.err.println(e.getMessage());
                executor.shutdown();
                executor = new ScheduledThreadPoolExecutor(2);
                executor.scheduleAtFixedRate(this, 0, schedulePeriodMin, TimeUnit.MINUTES);
            }
        }

        private static void watchAd(WebDriver driver, String username, String password, Long loginWaitMillis,
                                    Long adWaitMillis, Long afterAdQuitMillis, Long adLoopCount) throws InterruptedException {
            driver.manage().window().maximize();
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            driver.get("https://en.onlinesoccermanager.com/PrivacyNotice?nextUrl=%2FLogin");


            login(driver, username, password, loginWaitMillis);
            String originalHandle = driver.getWindowHandle();
            WebDriverWait wdw = new WebDriverWait(driver, Duration.ofMillis(loginWaitMillis));
            Optional<WebElement> agreeButton = driver.findElements(By.cssSelector("body > div.fc-consent-root > div.fc-dialog-container > div.fc-dialog.fc-choice-dialog > div.fc-footer-buttons-container > div.fc-footer-buttons > button.fc-button.fc-cta-consent.fc-primary-button")).stream().findFirst();
            agreeButton.ifPresent(WebElement::click);

            WebElement until = wdw.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#balances > div.wallet-container.bosscoin-wallet.btn-new.btn-success > div.wallet-amount.pull-left.center > div > span")));
            int startCoin = Integer.parseInt(driver.findElement(By.cssSelector("#balances > div.wallet-container.bosscoin-wallet.btn-new.btn-success > div.wallet-amount.pull-left.center > div > span")).getText());

            try {
                int currentCoin = watchAds(driver, adWaitMillis, adLoopCount);
                int gained = currentCoin - startCoin;
                if (gained < 9) {
                    System.out.println(new String("Eksik ".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) + (10 - gained) + new String("coin tamamlanıyor".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                    for (String handle : driver.getWindowHandles()) {
                        if (!handle.equals(originalHandle)) {
                            driver.switchTo().window(handle);
                            driver.close();
                        }
                    }
                    watchAds(driver, adWaitMillis, (long) (10 - gained));
                    return;
                }

            } catch (Exception e) {
                System.out.println(new String("Reklam butonuna tıklanamadı: ".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) + e.getMessage());
                throw e;
            }
            System.out.println(new String("Reklamlar İzlendi ".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) + afterAdQuitMillis + new String(" ms Sonra Kapanıyor".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
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
            WebElement currentCoin = driver.findElement(By.cssSelector("#balances > div.wallet-container.bosscoin-wallet.btn-new.btn-success > div.wallet-amount.pull-left.center > div > span"));
            System.out.println(new String("Reklamlar bitti Mevcut Boss Coin : ".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) + currentCoin.getText());
            driver.quit();
        }

        private static int watchAds(WebDriver driver, Long adWaitMillis, Long adLoopCount) throws InterruptedException {
            for (int i = 0; i < adLoopCount; i++) {
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
                Optional<WebElement> agreeButton = driver.findElements(By.cssSelector("body > div.fc-consent-root > div.fc-dialog-container > div.fc-dialog.fc-choice-dialog > div.fc-footer-buttons-container > div.fc-footer-buttons > button.fc-button.fc-cta-consent.fc-primary-button")).stream().findFirst();
                agreeButton.ifPresent(WebElement::click);
                WebElement currentCoin = driver.findElement(By.cssSelector("#balances > div.wallet-container.bosscoin-wallet.btn-new.btn-success > div.wallet-amount.pull-left.center > div > span"));
                System.out.println(new String("Mevcut Boss Coin : ".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) + currentCoin.getText());
                driver.switchTo().newWindow(WindowType.TAB);
                driver.get("https://en.onlinesoccermanager.com/BusinessClub");
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
                WebElement watchAdButton = driver.findElement(By.cssSelector("#body-content > div.row.row-h-sm-600.row-h-md-23.overflow-hidden.theme-stepover-0.businessclub-container > div.col-xs-12.col-h-xs-22.col-h-sm-20.businessclub-rows-container > div > div:nth-child(1) > div"));
                watchAdButton.click();
                System.out.println(new String("Reklam başlatıldı.".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                driver.manage().timeouts().implicitlyWait(Duration.ofMillis(adWaitMillis));
                List<WebElement> elements = driver.findElements(By.cssSelector("#modal-dialog-alert > div.row.row-h-xs-24.overflow-visible.modal-content-container > div > div > div > div.modal-header > h3"));
                if (!elements.isEmpty()) {
                    boolean isLimitReached = elements.get(0).getText().equalsIgnoreCase("Can't show video");
                    if (isLimitReached) {
                        System.out.println(new String("Reklam Limiti doldu çıkılıyor".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                        driver.navigate().refresh();
                        return Integer.parseInt(driver.findElement(By.cssSelector("#balances > div.wallet-container.bosscoin-wallet.btn-new.btn-success > div.wallet-amount.pull-left.center > div > span")).getText()) + 9;
                    }
                }

            }
            driver.navigate().to("https://en.onlinesoccermanager.com/BusinessClub");
            driver.navigate().refresh();
            WebElement currentCoin = driver.findElement(By.cssSelector("#balances > div.wallet-container.bosscoin-wallet.btn-new.btn-success > div.wallet-amount.pull-left.center > div > span"));
            return Integer.parseInt(currentCoin.getText());
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
            new Actions(driver).moveToElement(actualLoginButton).build().perform();
            actualLoginButton.submit();
            WebDriverWait wdw = new WebDriverWait(driver, Duration.ofMillis(loginWaitMillis));
            WebElement until = wdw.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#manager-name")));
            if (until != null) {
                System.out.println(new String("Login işlemi başarılı".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
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
                WebDriverWait wdw2 = new WebDriverWait(driver, Duration.ofMillis(loginWaitMillis));
                WebElement until1 = wdw2.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#manager-name")));
                if (until1.isDisplayed()) {
                    System.out.println(new String("Login işlemi başarılı".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                    return;
                } else {
                    System.err.println(new String("Login işlemi başarısız".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
                    throw new RuntimeException();
                }
            }
        }
    }


}