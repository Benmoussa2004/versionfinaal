package com.example.demo.Service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.example.demo.Entity.Ordre;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.scheduling.annotation.Async;
import java.text.SimpleDateFormat;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Async
    public void sendEmail(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            // Note: Gmail may ignore this if it doesn't match the authenticated user
            message.setFrom("commercial.lumiere@lumiere.tn");
            mailSender.send(message);
            System.out.println("Email sent successfully to: " + to);
        } catch (Exception e) {
            System.err.println("Error sending email to " + to + ": " + e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    @Async
    public void sendRegistrationEmail(String to, String name) {
        String subject = "Confirmation d'inscription";
        String title = "Confirmation d'inscription";
        String message = "Bonjour <b>" + name + "</b>,<br><br>" +
                "Votre demande d'inscription a bien été reçue. Elle est actuellement en attente de validation par un administrateur.<br>"
                +
                "Vous recevrez un email dès que votre compte sera activé.";

        String htmlContent = generateGenericHtml(title, message, "Accéder au site");
        try {
            sendHtmlEmailWithLogo(to, subject, htmlContent);
        } catch (MessagingException e) {
            System.err.println("Failed to send registration email to " + to + ": " + e.getMessage());
        }
    }

    @Async
    public void sendAccountActivatedEmail(String to, String name) {
        String subject = "Compte activé";
        String title = "Félicitations ! Votre compte est actif";
        String message = "Bonjour <b>" + name + "</b>,<br><br>" +
                "Votre compte a été activé par un administrateur.<br>" +
                "Vous pouvez désormais vous connecter à l'application.";

        String htmlContent = generateGenericHtml(title, message, "Se connecter");
        try {
            sendHtmlEmailWithLogo(to, subject, htmlContent);
        } catch (MessagingException e) {
            System.err.println("Failed to send activation email to " + to + ": " + e.getMessage());
        }
    }

    @Async
    public void sendRegistrationAcceptedEmail(String to, String name) {
        String subject = "Inscription acceptée";
        String title = "Bonne nouvelle ! Inscription acceptée";
        String message = "Bonjour <b>" + name + "</b>,<br><br>" +
                "Votre demande d'inscription a été acceptée par notre équipe commerciale.<br>" +
                "Un administrateur ou un commercial va maintenant compléter votre profil client pour activer définitivement votre accès.";

        String htmlContent = generateGenericHtml(title, message, "Voir mon profil");
        try {
            sendHtmlEmailWithLogo(to, subject, htmlContent);
        } catch (MessagingException e) {
            System.err.println("Failed to send acceptance email to " + to + ": " + e.getMessage());
        }
    }

    @Async
    public void sendAccountRejectedEmail(String to, String name) {
        String subject = "Inscription refusée";
        String title = "Information sur votre demande";
        String message = "Bonjour <b>" + name + "</b>,<br><br>" +
                "Nous avons le regret de vous informer que votre demande d'inscription n'a pas pu être retenue pour le moment.";

        String htmlContent = generateGenericHtml(title, message, "Nous contacter");
        try {
            sendHtmlEmailWithLogo(to, subject, htmlContent);
        } catch (MessagingException e) {
            System.err.println("Failed to send rejection email to " + to + ": " + e.getMessage());
        }
    }

    @Async
    public void sendNewRegistrationNotificationToAdmins(String newUserName, String newUserEmail) {
        String[] adminEmails = { "admin@lumiere.tn", "commercial@lumiere.tn" };
        String subject = "🔔 Nouvelle demande d'inscription";
        String title = "Action requise : Nouvelle inscription";
        String message = "Une nouvelle demande d'inscription a été reçue :<br><br>" +
                "📝 <b>Nom :</b> " + newUserName + "<br>" +
                "📧 <b>Email :</b> " + newUserEmail + "<br><br>" +
                "Veuillez vous connecter au panneau d'administration pour approuver ou rejeter cette demande.";

        String htmlContent = generateGenericHtml(title, message, "Gérer les utilisateurs");

        for (String adminEmail : adminEmails) {
            try {
                sendHtmlEmailWithLogo(adminEmail, subject, htmlContent);
            } catch (MessagingException e) {
                System.err.println("Failed to send notification to " + adminEmail + ": " + e.getMessage());
            }
        }
    }

    @Async
    public void sendOrderCreatedEmail(Ordre ordre) {
        String subject = "🔔 Nouvelle Demande de Livraison - " + ordre.getOrderNumber();
        String title = "Nouvelle Demande de Livraison";
        String message = "Une nouvelle demande de livraison a été soumise par le client <b>" + ordre.getNomclient() + "</b>.";

        String htmlContent = generateOrderHtml(ordre, title, message, "Consulter l'ordre");

        // Send to admins/commercials
        String[] adminEmails = { "admin@lumiere.tn", "commercial@lumiere.tn" };
        for (String email : adminEmails) {
            try {
                sendHtmlEmailWithLogo(email, subject, htmlContent);
            } catch (MessagingException e) {
                System.err.println("Failed to send order created email to " + email + ": " + e.getMessage());
            }
        }
    }

    @Async
    public void sendOrderConfirmedEmail(Ordre ordre, String clientEmail) {
        if (clientEmail == null || clientEmail.isEmpty()) {
            System.err.println("Cannot send order confirmation: client email is missing for order " + ordre.getOrderNumber());
            return;
        }

        String subject = "✅ Confirmation de votre Livraison - " + ordre.getOrderNumber();
        String title = "Votre Livraison est Confirmée";
        String message = "Bonjour <b>" + ordre.getNomclient() + "</b>,<br><br>" +
                "Nous avons le plaisir de vous informer que votre demande de livraison a été acceptée et planifiée.";

        String htmlContent = generateOrderHtml(ordre, title, message, "Suivre ma livraison");

        try {
            sendHtmlEmailWithLogo(clientEmail, subject, htmlContent);
        } catch (MessagingException e) {
            System.err.println("Failed to send order confirmation email to " + clientEmail + ": " + e.getMessage());
        }
    }

    private void sendHtmlEmailWithLogo(String to, String subject, String htmlContent) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(to);
        helper.setFrom("tnlumiere@gmail.com"); // Match authenticated username
        helper.setSubject(subject);
        helper.setText(htmlContent, true);

        // Add Logo
        ClassPathResource res = new ClassPathResource("static/assets/lum.jpg");
        if (res.exists()) {
            helper.addInline("lumiereLogo", res);
        } else {
            // Try another common path just in case
            ClassPathResource resAlt = new ClassPathResource("lum.jpg");
            if (resAlt.exists()) {
                helper.addInline("lumiereLogo", resAlt);
            }
        }

        mailSender.send(message);
        System.out.println("HTML Email with logo sent successfully to: " + to);
    }

    private String generateOrderHtml(Ordre ordre, String title, String message, String buttonText) {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        String chargementDate = ordre.getChargementDate() != null ? sdf.format(ordre.getChargementDate()) : "N/A";
        String livraisonDate = ordre.getLivraisonDate() != null ? sdf.format(ordre.getLivraisonDate()) : "N/A";

        String detailsTable = "<div class='details'>" +
                "<h3>Détails de la livraison</h3>" +
                "<table>" +
                "<tr><td class='label'>Numéro d'ordre:</td><td>" + ordre.getOrderNumber() + "</td></tr>" +
                "<tr><td class='label'>Client:</td><td>" + ordre.getNomclient() + "</td></tr>" +
                "<tr><td class='label'>Lieu de chargement:</td><td>" + ordre.getChargementNom() + " ("
                + ordre.getChargementVille() + ")</td></tr>" +
                "<tr><td class='label'>Date chargement:</td><td>" + chargementDate + "</td></tr>" +
                "<tr><td class='label'>Lieu de livraison:</td><td>" + ordre.getLivraisonNom() + " ("
                + ordre.getLivraisonVille() + ")</td></tr>" +
                "<tr><td class='label'>Date livraison:</td><td>" + livraisonDate + "</td></tr>" +
                "<tr><td class='label'>Désignation:</td><td>" + ordre.getDesignation() + "</td></tr>" +
                "<tr><td class='label'>Poids:</td><td>" + (ordre.getPoids() != null ? ordre.getPoids() + " kg" : "N/A")
                + "</td></tr>" +
                "</table>" +
                "</div>";

        return buildHtmlLayout(title, message, detailsTable, buttonText);
    }

    private String generateGenericHtml(String title, String message, String buttonText) {
        return buildHtmlLayout(title, message, "", buttonText);
    }

    private String buildHtmlLayout(String title, String message, String customContent, String buttonText) {
        String brandColor = "#f07020";
        return "<!DOCTYPE html>" +
                "<html><head><meta charset='UTF-8'><style>" +
                "body { margin: 0; padding: 0; background-color: #f4f7f9; font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; }" +
                ".wrapper { background-color: #f4f7f9; padding: 40px 10px; }" +
                ".container { max-width: 600px; margin: auto; background: #ffffff; border-radius: 16px; overflow: hidden; box-shadow: 0 10px 30px rgba(0,0,0,0.05); border: 1px solid #eef2f6; }" +
                ".header { background: linear-gradient(135deg, #ffffff 0%, #fefefe 100%); padding: 35px 20px; text-align: center; border-bottom: 1px solid #f0f0f0; }" +
                ".content { padding: 40px 45px; color: #374151; }" +
                "h2 { color: " + brandColor + "; font-size: 24px; font-weight: 700; margin-top: 0; margin-bottom: 20px; }" +
                "p { font-size: 16px; line-height: 1.7; color: #4b5563; margin-bottom: 25px; }" +
                ".details { background-color: #f9fafb; padding: 25px; border-radius: 12px; margin-top: 30px; border: 1px solid #f1f5f9; }" +
                ".details h3 { margin-top: 0; font-size: 14px; text-transform: uppercase; letter-spacing: 1px; color: #6b7280; margin-bottom: 15px; border-bottom: 1px solid #e5e7eb; padding-bottom: 8px; }" +
                ".details table { width: 100%; border-collapse: collapse; }" +
                ".details td { padding: 10px 0; font-size: 14px; border-bottom: 1px solid #f1f5f9; }" +
                ".details td.label { font-weight: 600; color: #374151; width: 45%; }" +
                ".footer { background-color: #1f2937; color: #9ca3af; padding: 35px 20px; text-align: center; font-size: 13px; }" +
                ".footer b { color: #ffffff; font-size: 15px; display: block; margin-bottom: 10px; }" +
                ".btn { display: inline-block; padding: 14px 35px; background-color: " + brandColor + "; color: #ffffff !important; text-decoration: none; border-radius: 10px; font-weight: 700; font-size: 15px; box-shadow: 0 5px 15px rgba(240, 112, 32, 0.3); transition: transform 0.2s; }" +
                "</style></head><body>" +
                "<div class='wrapper'>" +
                "<div class='container'>" +
                "<div class='header'><img src='cid:lumiereLogo' alt='Lumiere Transport' style='max-height: 90px; width: auto;' /></div>" +
                "<div class='content'>" +
                "<h2>" + title + "</h2>" +
                "<p>" + message + "</p>" +
                customContent +
                "<div style='text-align: center; margin-top: 40px;'>" +
                "<a href='https://lumiere.tn' class='btn'>" + buttonText + "</a>" +
                "</div>" +
                "</div>" +
                "<div class='footer'>" +
                "<b>Lumière Transport & Logistique</b>" +
                "Expert en transport routier et solutions logistiques<br><br>" +
                "Zone Industrielle, Tunis, Tunisie<br>" +
                "Tél: +216 71 000 000 | Email: contact@lumiere.tn<br><br>" +
                "&copy; 2026 Lumière. Tous droits réservés." +
                "</div>" +
                "</div>" +
                "</div>" +
                "</body></html>";
    }
}