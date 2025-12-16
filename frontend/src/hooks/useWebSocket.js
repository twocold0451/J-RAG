import { useEffect, useRef } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import useAppStore from '../store/useAppStore';

const useWebSocket = () => {
  const { currentUser, updateDocument } = useAppStore();
  const clientRef = useRef(null);

  useEffect(() => {
    if (!currentUser) {
        if (clientRef.current) {
            clientRef.current.deactivate();
            clientRef.current = null;
        }
        return;
    }

    const token = localStorage.getItem('jwtToken');
    if (!token) return;

    // Assuming backend is on localhost:8080. 
    // In a real app, this should match the API base URL logic.
    const socketFactory = () => new SockJS('http://localhost:8080/ws'); 

    const client = new Client({
      webSocketFactory: socketFactory,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      // debug: function (str) {
      //   console.log('STOMP: ' + str);
      // },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    client.onConnect = function (frame) {
      console.log('WS Connected');
      
      client.subscribe('/user/queue/document-updates', (message) => {
        if (message.body) {
          try {
            const update = JSON.parse(message.body);
            // update structure: { documentId, status, progress, errorMessage }
            updateDocument(update.documentId, {
              status: update.status,
              progress: update.progress,
              errorMessage: update.errorMessage
            });
          } catch (e) {
            console.error("Failed to parse WS message", e);
          }
        }
      });
    };

    client.onStompError = function (frame) {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    client.activate();
    clientRef.current = client;

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
      }
    };
  }, [currentUser, updateDocument]);
};

export default useWebSocket;