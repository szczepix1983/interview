import {over, Client} from '@stomp/stompjs';
import * as SockJS from 'sockjs-client';
import {Injectable, OnDestroy} from '@angular/core';
import {
  IClientMessage,
  ISubscriber
} from 'src/app/services/websocket/messages';
import {Subject} from 'rxjs';

@Injectable()
export class WebsocketService implements OnDestroy {

  private url = 'http://localhost:9000/websocket';
  private connection: Client = null;
  private messagesQue = [];
  private connectionSubject = new Subject<boolean>();

  constructor() {
    this.connect();
  }

  ngOnDestroy(): void {
    this.disconnect();
  }

  private connect(): void {
    console.log('Initialize WebSocket Connection');
    const socket = new SockJS(this.url);
    this.connection = over(socket);
    this.connectionSubject.next(this.isConnected());
    this.connection.connect({}, (frame) => {
      this.messagesQue.forEach(message => this.send(message));
      this.connectionSubject.next(this.isConnected());
    });
  }

  private disconnect(): void {
    if (this.isConnected()) {
      this.connection.disconnect(() => {
        console.log('Disconnected');
        this.connectionSubject.next(this.isConnected());
      });
    }
  }

  isConnected(): boolean {
    return this.connection !== null && this.connection.ws && this.connection.ws.readyState === WebSocket.OPEN;
  }

  subscribe(subscriber: ISubscriber): void {
    if (this.isConnected()) {
      console.log('New subscriber for: ' + subscriber.topic);
      this.connection.subscribe(subscriber.topic, subscriber.callback);
    } else {
      console.log('Subscriber in buffer: ' + subscriber.topic);
      this.messagesQue.push(subscriber);
    }
  }

  send(message: IClientMessage): void {
    if (this.isConnected()) {
      console.log('Sending via web socket: ' + JSON.stringify(message));
      this.connection.send(message.topic, {}, JSON.stringify(message.content));
    } else {
      console.log('Message in buffer: ' + JSON.stringify(message));
      this.messagesQue.push(message);
    }
  }

  getConnection(): Client {
    return this.connection;
  }
}
