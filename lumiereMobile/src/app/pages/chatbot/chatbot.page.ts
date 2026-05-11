import { Component, OnInit, ViewChild, ElementRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  IonContent,
  IonHeader,
  IonToolbar,
  IonButton,
  IonIcon,
  IonFooter,
  IonTextarea
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { sparkles, notificationsOutline, logOutOutline, arrowBackOutline, trashOutline, send, infiniteOutline } from 'ionicons/icons';
import { NavController } from '@ionic/angular';
import { ChatbotService } from '../../services/chatbot.service';

interface Message {
  text: string;
  isUser: boolean;
  timestamp: Date;
}

@Component({
  selector: 'app-chatbot',
  templateUrl: './chatbot.page.html',
  styleUrls: ['./chatbot.page.scss'],
  standalone: true,
  imports: [
    IonContent,
    IonHeader,
    IonToolbar,
    IonButton,
    IonIcon,
    IonFooter,
    IonTextarea,
    CommonModule,
    FormsModule
  ]
})
export class ChatbotPage implements OnInit {
  @ViewChild(IonContent) content!: IonContent;

  messages: Message[] = [];
  userInput = '';
  isTyping = false;

  // Predefined responses
  constructor(
    public navCtrl: NavController,
    private chatbotService: ChatbotService
  ) {
    addIcons({ sparkles, notificationsOutline, logOutOutline, arrowBackOutline, trashOutline, send, infiniteOutline });
  }

  ngOnInit() {
    this.chatbotService.messages$.subscribe(msgs => {
      this.messages = msgs.map(m => ({
        text: m.content,
        isUser: m.sender === 'user',
        timestamp: m.timestamp
      }));
      // S'assurer que le scroll descend après le rendu
      this.scrollToBottom(500);
    });
  }

  goToNotifications() {
    this.navCtrl.navigateForward('/notifications');
  }

  logout() {
    this.navCtrl.navigateRoot('/login');
  }

  sendMessage() {
    const message = this.userInput.trim();
    if (!message) return;

    this.userInput = '';
    // Scroll immédiat pour le message de l'utilisateur
    this.scrollToBottom(100);

    // Call real API
    this.isTyping = true;
    this.chatbotService.sendMessage(message).subscribe({
      next: (botMsg) => {
        this.isTyping = false;
        // Check if bot created a reminder and sync to localStorage
        this.syncRappelIfNeeded(message, botMsg.content);
      },
      error: (err) => {
        this.isTyping = false;
        this.addBotMessage("Une erreur est survenue lors de la communication avec l'assistant.");
        console.error('Chatbot error:', err);
      }
    });
  }

  /**
   * If the chatbot created a reminder, sync it to localStorage
   * so it appears on the Rappel page
   */
  private syncRappelIfNeeded(userMessage: string, botResponse: string) {
    if (!botResponse) return;
    const lower = botResponse.toLowerCase();
    console.log('Checking for reminder in response:', lower);
    
    // Detect successful reminder creation (Must START with confirmation or contain the specific success emoji)
    const isSuccess = lower.startsWith('rappel créé') || 
                      lower.startsWith('rappel cree') ||
                      lower.includes('✅ rappel') ||
                      lower.includes('📅 rappel');

    if (isSuccess) {
      console.log('Reminder creation detected!');
      try {
        // Parse the time from the bot response
        const dateMatch = botResponse.match(/(\d{2}[-/]\d{2}[-/]\d{4})\s*[àa]\s*(\d{2}[h:]\d{2})/i)
                       || botResponse.match(/(\d{4}[-/]\d{2}[-/]\d{2})\s*[àa]\s*(\d{2}[h:]\d{2})/i);
        
        let rappelDate: string;
        if (dateMatch) {
          const datePart = dateMatch[1].replace(/\//g, '-');
          const timePart = dateMatch[2].replace('h', ':');
          // Handle DD-MM-YYYY format
          const parts = datePart.split('-');
          if (parts[0].length === 2) {
            rappelDate = new Date(`${parts[2]}-${parts[1]}-${parts[0]}T${timePart}:00`).toISOString();
          } else {
            rappelDate = new Date(`${datePart}T${timePart}:00`).toISOString();
          }
        } else {
          // Default: use tomorrow at noon
          const tomorrow = new Date();
          tomorrow.setDate(tomorrow.getDate() + 1);
          tomorrow.setHours(12, 0, 0, 0);
          rappelDate = tomorrow.toISOString();
        }

        // Extract the reminder text from original user message
        // Extract a clean title
        let titre = userMessage
          .replace(/rappel|rappelle|reminder|demain|aujourd'hui|matin|soir|après-midi|midi|ghodwa|sbeh|lil/gi, '')
          .replace(/^(aaslema|aslema|bonjour|salut|hey|svp|s'il vous plaît|please)\s*,?\s*/i, '')
          .trim();
        
        if (!titre) titre = userMessage;
        titre = titre.charAt(0).toUpperCase() + titre.slice(1);

        const rappel = {
          id: Date.now().toString(),
          titre: titre.charAt(0).toUpperCase() + titre.slice(1),
          note: 'Généré automatiquement par l\'IA OTFLOW',
          date: rappelDate,
          fait: false,
          source: 'ia'
        };

        const existing = localStorage.getItem('rappels');
        const rappels = existing ? JSON.parse(existing) : [];
        rappels.push(rappel);
        localStorage.setItem('rappels', JSON.stringify(rappels));
        console.log('Rappel synced to localStorage:', rappel);
      } catch (e) {
        console.error('Error syncing rappel:', e);
      }
    }
  }


  private addBotMessage(text: string) {
    this.messages.push({
      text,
      isUser: false,
      timestamp: new Date()
    });
    this.scrollToBottom();
  }

  private scrollToBottom(duration: number = 300) {
    if (this.content) {
      setTimeout(() => {
        this.content.scrollToBottom(duration);
      }, 150);
    }
  }

  getMessageTime(timestamp: Date): string {
    return new Date(timestamp).toLocaleTimeString('fr-FR', {
      hour: '2-digit',
      minute: '2-digit'
    });
  }

  public formatMessage(text: string): string {
    if (!text) return '';
    // Escape HTML
    let formatted = text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
    // Convert lines starting with '- ' into styled bullet items
    formatted = formatted.replace(/^- (.+)$/gm, '<span class="bullet-item">• $1</span>');
    // Convert remaining newlines to <br>
    formatted = formatted.replace(/\n/g, '<br>');
    return formatted;
  }

  clearChat() {
    this.chatbotService.clearHistory();
  }

  trackByFn(index: number, item: any) {
    return item.timestamp;
  }
}

