import { Injectable } from "@angular/core";
import { Network } from 'vis-network';
import { Data } from "@angular/router";

@Injectable({
  providedIn: 'root'
})
export class NetworkFactory {
  createNetwork(container: HTMLElement, data: Data): Network {
    return new Network(container, data, {
      height: '100%',
      width: '100%',
      physics: {
        enabled: true,
        stabilization: false,
        solver: "repulsion",
        repulsion: {
          nodeDistance: 600
        }
      },
      interaction: {
        dragView: true
      },
      nodes: {
        shape: 'square',
        color: '#3366ff',
        size: 10,
        font: {
          color: 'white',
        },
      },
      edges: {
        smooth: false,
        arrows: {
          to: {
            enabled: true,
            type: 'vee',
          },
        },
      },
    });

  }
}