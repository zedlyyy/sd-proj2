# tukano2

## Overview

Academic distributed-systems project implementing and testing a Java-based short-video sharing service. The project explores REST and gRPC interfaces, TLS-enabled communication, replication, failure recovery, and supporting services used during testing.

**Technologies and concepts:** Java, Maven, Docker, REST, gRPC, TLS, distributed replication, Kafka/Zookeeper test infrastructure.

**Testing focus:** End-to-end operations, server failures, replication, and recovery scenarios using course test scripts.

---


# TESTING             
## 1. Build the image (run in your project folder):	
    » build on windows terminal and not WSL because its much slower in WSL

      	mvn clean compile assembly:single docker:build

## 2. Test with Prof's TESTER
    flags: 
        -test <num> : to jump for a test
        -services true : to use (Kafka, Zookeeper) in all tests
        -sleep 2 : time between launching server and starting tests (not 1 because it gives errors, depending on machine capabilities); 
        -kafkasleep seconds : time between launching kafka servers and starting tests 
        -skip true : não verifica as keystores.
        -log OFF|ALL|FINE|FINEST : to different levels of details 
        -textsize <len> : Permite indicar a dimensão máxima do texto das mensagens criadas.
        -failsleep <seconds> : pró tester esperar mais tempo nos testes de falhas
        -trace : pra ver as exceções lançadas

    common error:
        - not having a kafka image. To solve run command: 
            docker pull smduarte/sd2324-kafka:latest

    RUN on Windows 
        TP2
            .\test-sd-tp2.bat -image sd2324-tp2-63312-63585     -test 105c      -services false    -skip true   -sleep 3  -trace -failsleep 8
            
            tests: 
                proxy REST, usando TLS:
                    Mix de operações (105a).                                        ok
                    Mix de operações + remoção de utilizador (105b).                ok
                    Mix de operações com falha e recuperação do servidor (105c).    ok
                no proxy REST + blob REST, usando TLS:
                    Mix de operações (105d).                                        ok
                REST, usando TLS:
                    Mix de operações + falha e recuperação de servidor (106a)       ok
                REST com múltiplos servidores de blobs, usando TLS:
                    Mix de operações + verifica replicação em duas réplicas e balanceamento (107a)      ok
                    Mix de operações + verifica replicação em duas réplicas após falha (107b)           ok
                    Mix de operações + verifica replicação em duas réplicas após falha (107c)           ok
                REST com servidores short replicados, sem falhas, usando TLS:
                    Mix de operações simples (108a)
                    Mix de operações (108b)
                    Mix de operações + remove user (108c)
                REST com servidores short replicados, usando TLS:
                    Mix de operações com falha de comunicação com réplica 1 (109a)
                    Mix de operações com falha de comunicação com réplica 2 (109b)
                    Mix de operações com falha de réplica 1 sem recuperação (110a)
                    Mix de operações com falha de réplica 2 sem recuperação (110b)
                    Mix de operações com falha de réplica 1 com recuperação (110c)
                    Mix de operações com falha de réplica 2 com recuperação (110d)
                    Mix de operações com falha de réplica 0 sem recuperação (110e)
                    Mix de operações com falha de réplica 0 com recuperação (110f)

        TP1           
            .\test-sd-tp1.bat -image sd2324-tp2-63312-63585 -test 3a -sleep 2

            tests: 
                Discovery - Anúnicos com discovery (3a)
                REST (5c - 6b)
                    Shorts+Blobs: create, get, get feed, follow/unfollow, delete (5c)
                    Shorts+Blobs:create, get, get feed, follow/unfollow, like/unlike, delete (5d)
                    Shorts+Blobs:create, get, get feed, follow/unfollow, like/unlike, delete, delete user (5e)
                    Shorts+Blobs: create, get (6a)
                    Shorts+Blobs:create, get, get feed, follow/unfollow, like/unlike, delete (6b)
                gRPC (9a - 10d)
                    criação e leitura de utilizadores (9a)
                    atualização de utilizadores (9b)
                    remoção de utilizadores (9c)
                    pesquisa de utilizadores (9d)
                    Shorts only: create, get (10a)
                    Shorts+Blobs: create, get (10b)
                    Shorts+Blobs: create, get, get feed, follow/unfollow (10c)
                    Shorts+Blobs:create, get, get feed, follow/unfollow, like/unlike, delete (10d)
                REST+gRPC
                    Shorts+Blobs: create, get, get feed, follow/unfollow, delete (12a)
                    Shorts+Blobs: create, get, get feed, follow/unfollow, delete (12b)
                    Shorts+Blobs: create, get, get feed, follow/unfollow, delete (12c)
                    Shorts+Blobs: create, get, get feed, follow/unfollow, delete (12d)
                    Shorts+Blobs: create, get, get feed, follow/unfollow, delete (12e)


    RUN on Linux/Mac
        sh test-sd-tp1.sh -image sd2324-tp2-63312-63585  -sleep 2

