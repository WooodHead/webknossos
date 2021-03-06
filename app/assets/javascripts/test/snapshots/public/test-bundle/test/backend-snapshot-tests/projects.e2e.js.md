# Snapshot report for `public/test-bundle/test/backend-snapshot-tests/projects.e2e.js`

The actual snapshot is saved in `projects.e2e.js.snap`.

Generated by [AVA](https://ava.li).

## projects-createProject(project: APIProjectType)

    {
      expectedTime: 60000,
      id: 'fixed-project-id',
      name: 'test-new-project',
      owner: {
        email: 'scmboy@scalableminds.com',
        firstName: 'SCM',
        id: '570b9f4d2a7c0e4d008da6ef',
        isAnonymous: false,
        lastName: 'Boy',
        teams: [
          {
            role: {
              name: 'admin',
            },
            team: 'Connectomics department',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test1',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test2',
          },
        ],
      },
      paused: false,
      priority: 1,
      team: 'Connectomics department',
    }

## projects-deleteProject(projectName: string)

    {
      messages: [
        {
          success: 'Project was removed',
        },
      ],
    }

## projects-getProject(projectName: string)

    {
      expectedTime: 90,
      id: '58135bfd2faeb3190181c057',
      name: 'Test_Project',
      owner: {
        email: 'scmboy@scalableminds.com',
        firstName: 'SCM',
        id: '570b9f4d2a7c0e4d008da6ef',
        isAnonymous: false,
        lastName: 'Boy',
        teams: [
          {
            role: {
              name: 'admin',
            },
            team: 'Connectomics department',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test1',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test2',
          },
        ],
      },
      paused: false,
      priority: 100,
      team: 'Connectomics department',
    }

## projects-getProjects()

    [
      {
        expectedTime: 90,
        id: '58135bfd2faeb3190181c057',
        name: 'Test_Project',
        owner: {
          email: 'scmboy@scalableminds.com',
          firstName: 'SCM',
          id: '570b9f4d2a7c0e4d008da6ef',
          isAnonymous: false,
          lastName: 'Boy',
          teams: [
            {
              role: {
                name: 'admin',
              },
              team: 'Connectomics department',
            },
            {
              role: {
                name: 'admin',
              },
              team: 'test1',
            },
            {
              role: {
                name: 'admin',
              },
              team: 'test2',
            },
          ],
        },
        paused: false,
        priority: 100,
        team: 'Connectomics department',
      },
    ]

## projects-getProjectsWithOpenAssignments()

    [
      {
        expectedTime: 90,
        id: '58135bfd2faeb3190181c057',
        name: 'Test_Project',
        numberOfOpenAssignments: 19,
        owner: {
          email: 'scmboy@scalableminds.com',
          firstName: 'SCM',
          id: '570b9f4d2a7c0e4d008da6ef',
          isAnonymous: false,
          lastName: 'Boy',
          teams: [
            {
              role: {
                name: 'admin',
              },
              team: 'Connectomics department',
            },
            {
              role: {
                name: 'admin',
              },
              team: 'test1',
            },
            {
              role: {
                name: 'admin',
              },
              team: 'test2',
            },
          ],
        },
        paused: false,
        priority: 100,
        team: 'Connectomics department',
      },
    ]

## projects-pauseProject(projectName: string)

    {
      expectedTime: 90,
      id: '58135bfd2faeb3190181c057',
      name: 'Test_Project',
      owner: {
        email: 'scmboy@scalableminds.com',
        firstName: 'SCM',
        id: '570b9f4d2a7c0e4d008da6ef',
        isAnonymous: false,
        lastName: 'Boy',
        teams: [
          {
            role: {
              name: 'admin',
            },
            team: 'Connectomics department',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test1',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test2',
          },
        ],
      },
      paused: true,
      priority: 100,
      team: 'Connectomics department',
    }

## projects-resumeProject(projectName: string)

    {
      expectedTime: 90,
      id: '58135bfd2faeb3190181c057',
      name: 'Test_Project',
      owner: {
        email: 'scmboy@scalableminds.com',
        firstName: 'SCM',
        id: '570b9f4d2a7c0e4d008da6ef',
        isAnonymous: false,
        lastName: 'Boy',
        teams: [
          {
            role: {
              name: 'admin',
            },
            team: 'Connectomics department',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test1',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test2',
          },
        ],
      },
      paused: false,
      priority: 100,
      team: 'Connectomics department',
    }

## projects-revertedProject

    {
      expectedTime: 5400000,
      id: '58135bfd2faeb3190181c057',
      name: 'Test_Project',
      owner: {
        email: 'scmboy@scalableminds.com',
        firstName: 'SCM',
        id: '570b9f4d2a7c0e4d008da6ef',
        isAnonymous: false,
        lastName: 'Boy',
        teams: [
          {
            role: {
              name: 'admin',
            },
            team: 'Connectomics department',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test1',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test2',
          },
        ],
      },
      paused: false,
      priority: 100,
      team: 'Connectomics department',
    }

## projects-updateProject(projectName: string, project: APIProjectType)

    {
      expectedTime: 5400000,
      id: '58135bfd2faeb3190181c057',
      name: 'Test_Project',
      owner: {
        email: 'scmboy@scalableminds.com',
        firstName: 'SCM',
        id: '570b9f4d2a7c0e4d008da6ef',
        isAnonymous: false,
        lastName: 'Boy',
        teams: [
          {
            role: {
              name: 'admin',
            },
            team: 'Connectomics department',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test1',
          },
          {
            role: {
              name: 'admin',
            },
            team: 'test2',
          },
        ],
      },
      paused: false,
      priority: 1337,
      team: 'Connectomics department',
    }
