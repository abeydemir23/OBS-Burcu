package org.example;

import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
//        System.setProperty("webdriver.chrome.driver", "path/to/chromedriver");

        // WebDriver'ı başlat
        WebDriver driver = new ChromeDriver();

        // Sayfa yüklenmesini beklemek için implicit wait kullan
        driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

        // İlgili URL'ye git
        driver.get("https://en.onlinesoccermanager.com/PrivacyNotice?nextUrl=%2FLogin");


        WebElement button = driver.findElement(By.cssSelector("#page-privacynotice > div > div > div:nth-child(2) > div:nth-child(3) > div > button"));

        // div altındaki button elementini bul ve tıkla
//        WebElement button = divElement.findElement(By.tagName("button"));

        button.click();

        WebElement loginButton = driver.findElement(By.cssSelector("#page-signup > div.page.content.hidden-before-binding > div.register-information-container.horizontal-center-absolute > div.register-information-block.buttons > button"));
        loginButton.click();




        WebElement username = driver.findElement(By.cssSelector("#manager-name"));
        WebElement password = driver.findElement(By.cssSelector("#password"));
        WebElement actualLoginButton = driver.findElement(By.cssSelector("#login"));
        Thread.currentThread().sleep(2000);
        actualLoginButton.click();
        Thread.currentThread().sleep(5000);
                driver.navigate().to("https://en.onlinesoccermanager.com/ChooseLeague/");


//
        FluentWait<WebDriver> wait = new FluentWait<>(driver)
                .withTimeout(Duration.ofSeconds(10))
                .pollingEvery(Duration.ofSeconds(5))
                .ignoring(NoSuchElementException.class);
                        wait.until(ExpectedConditions.urlContains("https://en.onlinesoccermanager.com/ChooseLeague/"));

//
//        synchronized (wait) {
//            try {
//
//
//            }
//            catch (Exception e){
//                e.printStackTrace();
//            }
//        }

        driver.navigate().to("https://en.onlinesoccermanager.com/BusinessClub");


//        WebElement acceptButton = driver.findElement(By.className("btn-new btn-orange"));
//        acceptButton.click();


        // "Watch Ad" butonunu bul ve tıkla
        try {
//            driver.get("https://en.onlinesoccermanager.com/BusinessClub");

            // Butonu bul (CSS Selector, Xpath veya ID gibi uygun bir yöntem kullanabilirsiniz)
            WebElement watchAdButton = driver.findElement(By.cssSelector("#body-content > div.row.row-h-sm-600.row-h-md-23.overflow-hidden.theme-stepover-0.businessclub-container > div.col-xs-12.col-h-xs-22.col-h-sm-20.businessclub-rows-container > div > div:nth-child(1) > div"));
            watchAdButton.click();

            System.out.println("Reklam başlatıldı.");

        } catch (Exception e) {
            System.out.println("Reklam butonuna tıklanamadı: " + e.getMessage());
        }

//         Tarayıcıyı kapat
//         driver.quit(); // Eğer işlemler bitince kapatmak isterseniz.
    }

}